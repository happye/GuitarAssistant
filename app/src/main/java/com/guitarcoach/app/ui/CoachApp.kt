package com.guitarcoach.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guitarcoach.app.data.AppContainer
import com.guitarcoach.app.ui.screens.HomeScreen
import com.guitarcoach.app.ui.screens.PracticeScreen
import com.guitarcoach.app.ui.screens.ProfileScreen
import com.guitarcoach.app.ui.screens.TabStudioScreen
import com.guitarcoach.app.ui.screens.TheoryScreen

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun CoachApp(container: AppContainer) {
    val navController = rememberNavController()
    val tabs = listOf(
        TabItem("home", "首页", Icons.Outlined.Home),
        TabItem("practice", "练习室", Icons.Outlined.Tune),
        TabItem("tabs", "识谱", Icons.Outlined.MenuBook),
        TabItem("theory", "乐理", Icons.Outlined.School),
        TabItem("me", "我的", Icons.Outlined.Person),
    )
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"

    // 系统返回 = 切回首页（saveState 路径，与底部 Tab 切换同语义），而不是弹栈销毁——
    // 否则识谱工作台扒出的谱面/粘贴内容会被返回键直接丢掉（用户数据丢失，真机实测确认）。
    // 生效依赖：本 BackHandler 组合晚于 NavHost 内部的 PredictiveBackHandler（后者首组合注册、
    // enabled 翻转不重注册），OnBackPressedDispatcher 后加者优先——升级 activity-compose 后须真机复验此行为
    if (currentRoute != "home") {
        BackHandler {
            navController.navigate("home") {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") { HomeScreen(container) }
            composable("practice") { PracticeScreen(container) }
            composable("tabs") { TabStudioScreen(container) }
            composable("theory") { TheoryScreen(container) }
            composable("me") { ProfileScreen(container) }
        }
    }
}
