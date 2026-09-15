package com.guitarcoach.app.core.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * 端侧手部 21 关键点检测（MediaPipe Tasks Vision）。
 *
 * 模型文件 hand_landmarker.task 需放到 app/src/main/assets/ 下，
 * 官方下载地址（float16，约 7MB）：
 * https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
 *
 * 当前提供单帧同步识别（IMAGE 模式），服务「关键帧抽样 → LLM 点评」链路；
 * M3 做实时叠加时切到 LIVE_STREAM 模式（detectAsync + resultListener），接口不变。
 */
class HandLandmarkerHelper(context: Context) : AutoCloseable {

    private val landmarker: HandLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    /** 检测一帧；未检测到手时 result 手列表为空。 */
    fun detect(bitmap: Bitmap): HandLandmarkerResult =
        landmarker.detect(BitmapImageBuilder(bitmap).build())

    override fun close() {
        landmarker.close()
    }
}
