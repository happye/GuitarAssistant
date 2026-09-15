package com.guitarcoach.app.core.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 相机帧 → 压缩 JPEG → base64。
 * 图片在送 LLM 之前统一压缩，是控制 token 成本与上行延迟的第一道闸门：
 * 1024px + 质量 72 的 JPEG 通常 80~150KB，多模态模型约消耗数百 token 视觉额度。
 */
object FrameCodec {

    /** 等比缩放到 maxSide 以内再编码；返回纯 base64（不带 data: 前缀）。 */
    fun toBase64Jpeg(source: Bitmap, maxSide: Int = 1024, quality: Int = 72): String {
        val scaled = scaleDown(source, maxSide)
        val bos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun decode(base64: String): Bitmap? {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
