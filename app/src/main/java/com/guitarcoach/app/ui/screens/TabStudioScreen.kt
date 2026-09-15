package com.guitarcoach.app.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun TabStudioScreen() {
    PlaceholderScreen(
        title = "识谱工作台",
        subtitle = "拍一张谱 → AI 识别成可交互的谱面 → 逐段讲解怎么弹。底层管线已在 M0 就绪。",
        todo = listOf(
            "拍照 / 相册选谱（CameraX + Photo Picker）",
            "LLM 视觉识谱 → TabDocument（LlmTabExtractor 已实现，等 UI 接入）",
            "谱面渲染 + 点按试听（自绘 Canvas，或引入 alphaTab）",
            "Guitar Pro (.gp) 文件导入、Ultimate Guitar 文本谱粘贴解析（TextTabParser 已实现）",
            "「这段怎么弹」逐段教学讲解（CoachOrchestrator.explainTabImage 已实现）",
        ),
    )
}
