package com.guitarcoach.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.guitarcoach.app.core.audio.TtsController
import com.guitarcoach.app.core.coach.CoachFeedback
import com.guitarcoach.app.core.vision.LiveHandLandmarker
import com.guitarcoach.app.core.vision.PostureRules
import com.guitarcoach.app.core.vision.FrameCodec
import com.guitarcoach.app.data.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** 手部骨架连线（MediaPipe 21 点拓扑）。 */
private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 17 to 18, 18 to 19, 19 to 20, 0 to 17,
)

/**
 * M3 视觉教练 v1（F301+F302+F303）：后置相机实时手部跟踪 → 骨架叠加（F301）→
 * 规则警报横幅（F302，0ms 层 ≤2 条）→ 每 100s 关键帧送 LLM 点评 + TTS 播报（F303）。
 * 口径：手不在画面只给出框引导、绝不产生点评（防幻觉）；单目不判品（L005）。
 */
@Composable
internal fun PostureCoachScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val tts = remember { TtsController(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    val hands = remember { mutableStateOf<List<List<PostureRules.Pt>>>(emptyList()) }
    val alerts = remember { mutableStateOf<List<PostureRules.Alert>>(emptyList()) }
    val inFrame = remember { mutableStateOf(true) }
    val lastFrame = remember { mutableStateOf<Bitmap?>(null) }
    val reviewing = remember { mutableStateOf(false) }
    val lastReviewAt = remember { mutableStateOf(0L) }
    var review by remember { mutableStateOf<CoachFeedback?>(null) }

    val landmarker = remember {
        LiveHandLandmarker(context) { h, _ ->
            hands.value = h
            inFrame.value = PostureRules.allInFrame(h)
            alerts.value = h.take(2).flatMap { PostureRules.check(it) }
        }
    }
    DisposableEffect(Unit) { onDispose { landmarker.close() } }

    // F303：手在框内 + 距上次点评 ≥100s（方案 90~120s 取中）→ 采当前帧送 LLM → TTS
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        while (isActive) {
            delay(5_000)
            val now = System.currentTimeMillis()
            val frame = lastFrame.value
            if (frame != null && inFrame.value && !reviewing.value && now - lastReviewAt.value > 100_000) {
                reviewing.value = true
                lastReviewAt.value = now
                try {
                    val base64 = withContext(Dispatchers.IO) { FrameCodec.toBase64Jpeg(frame) }
                    val feedback = container.coach.reviewHandFrames(listOf(base64))
                    review = feedback
                    feedback?.let {
                        tts.speak(it.overall + (it.issues.firstOrNull()?.fix ?: ""))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    review = null // 点评失败静默等待下一轮，不打断练习
                } finally {
                    reviewing.value = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("视觉教练", style = MaterialTheme.typography.titleMedium)
            Text(" ", style = MaterialTheme.typography.titleMedium)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (hasPermission) {
                CameraPreview(
                    onFrame = { bitmap, ts ->
                        lastFrame.value = bitmap
                        landmarker.detectAsync(bitmap, ts)
                    },
                )
                HandOverlay(hands.value)
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("需要相机权限才能实时纠手型")
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("授权相机") }
                }
            }
            // F302 警报横幅（0ms 层）
            if (!inFrame.value) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                ) {
                    Text(
                        "双手没进画面——把手放到画面中央，琴颈斜对镜头",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                alerts.value.take(2).forEachIndexed { i, alert ->
                    Surface(
                        color = if (alert.severity == "high") {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = (8 + i * 44).dp),
                    ) {
                        Text("⚠ ${alert.message}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // F303 点评卡
        review?.let { fb ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(fb.overall, style = MaterialTheme.typography.bodyMedium)
                    fb.issues.forEach { issue ->
                        Text(
                            "· ${issue.what} → ${issue.fix}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { tts.stop() }) { Text("停止播报") }
                }
            }
        }
        if (reviewing.value) {
            Text(
                "正在请教练看你的关键帧…",
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CameraPreview(onFrame: (Bitmap, Long) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // 退出顺序：先解绑相机（停帧派发、释放硬件），再关线程池——反过来会有 RejectedExecution 崩溃
    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val p = providerFuture.get()
                provider = p
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { ana ->
                        ana.setAnalyzer(executor) { proxy ->
                            try {
                                val bitmap = proxy.toBitmap()
                                val ts = proxy.imageInfo.timestamp / 1_000_000
                                onFrame(bitmap, ts)
                            } finally {
                                proxy.close()
                            }
                        }
                    }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

/** F301 骨架叠加：21 点连线按画面尺寸拉伸绘制（v1 简化：不做裁剪对齐）。 */
@Composable
private fun HandOverlay(hands: List<List<PostureRules.Pt>>) {
    val lineColor = Color(0xFF4CD964)
    Canvas(modifier = Modifier.fillMaxSize()) {
        hands.forEach { hand ->
            if (hand.size < 21) return@forEach
            HAND_CONNECTIONS.forEach { (a, b) ->
                val pa = hand[a]
                val pb = hand[b]
                drawLine(
                    lineColor,
                    Offset(pa.x * size.width, pa.y * size.height),
                    Offset(pb.x * size.width, pb.y * size.height),
                    strokeWidth = 4f,
                )
            }
            hand.forEach { p ->
                drawCircle(Color.White, radius = 6f, center = Offset(p.x * size.width, p.y * size.height))
            }
        }
    }
}
