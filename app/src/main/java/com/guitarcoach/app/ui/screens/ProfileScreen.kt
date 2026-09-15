package com.guitarcoach.app.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun ProfileScreen() {
    PlaceholderScreen(
        title = "我的",
        subtitle = "练习记录、曲目库、复盘报告都会出现在这里。",
        todo = listOf(
            "练习记录（Room：日期 / 时长 / 练了什么 / 调音与手型问题留档）",
            "曲目库：导入的谱面管理、进度标记",
            "周报：AI 复盘本周练习，给下周建议",
            "目标与打卡：每日练习提醒",
        ),
    )
}
