package com.guitarcoach.app.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * F301 实时手部检测（LIVE_STREAM + GPU 优先，失败回退 CPU）：
 * 相机分析帧经 [detectAsync] 送入，结果经回调线程返回（调用方自行切主线程）。
 * 时间戳必须单调递增（MediaPipe LIVE_STREAM 契约）——resultListener 不回传时间戳，
 * 内部用 FIFO 队列把 detectAsync 的时间戳配对给结果。
 */
class LiveHandLandmarker(
    context: Context,
    private val onResult: (hands: List<List<PostureRules.Pt>>, timestampMs: Long) -> Unit,
) : AutoCloseable {

    private companion object {
        const val LOG_TAG = "GuitarCoach"
    }

    private val landmarker: HandLandmarker
    private val pendingTimestamps = ConcurrentLinkedQueue<Long>()

    init {
        fun buildOptions(delegate: Delegate): HandLandmarker.HandLandmarkerOptions =
            HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("hand_landmarker.task")
                        .setDelegate(delegate)
                        .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    val ts = pendingTimestamps.poll() ?: 0L
                    onResult(result.toHands(), ts)
                }
                .build()

        landmarker = try {
            HandLandmarker.createFromOptions(context, buildOptions(Delegate.GPU))
        } catch (e: Exception) {
            Log.w(LOG_TAG, "GPU delegate unavailable, fall back to CPU", e)
            HandLandmarker.createFromOptions(context, buildOptions(Delegate.CPU))
        }
    }

    /** 送一帧进检测管线。时间戳必须递增，否则 MediaPipe 静默跳过该帧。 */
    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        pendingTimestamps.add(timestampMs)
        runCatching {
            landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
        }.onFailure { t ->
            pendingTimestamps.remove(timestampMs)
            Log.e(LOG_TAG, "detectAsync failed", t)
        }
    }

    override fun close() {
        landmarker.close()
    }

    private fun HandLandmarkerResult.toHands(): List<List<PostureRules.Pt>> =
        this.landmarks().map { hand -> hand.map { lm -> PostureRules.Pt(lm.x(), lm.y()) } }
}
