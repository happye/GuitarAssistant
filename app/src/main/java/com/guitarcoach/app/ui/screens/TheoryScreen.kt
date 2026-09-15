package com.guitarcoach.app.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun TheoryScreen() {
    PlaceholderScreen(
        title = "乐理教练",
        subtitle = "像跟老师聊天一样问乐理：降key升key、五度圈、移调、变调夹……每个术语都配大白话解释和吉他上的具体操作。",
        todo = listOf(
            "流式聊天 UI（CoachOrchestrator.askTheory 已实现，等 UI 接入）",
            "语音提问（sherpa-onnx 端侧中文 ASR 或云端 ASR）",
            "常用概念卡片：调 / 移调 / 变调夹 / 五度圈 / 强力和弦",
            "指板可视化：点一个音看它在每一根弦的位置",
        ),
    )
}
