package com.guitarcoach.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.guitarcoach.app.data.AppContainer
import com.guitarcoach.app.ui.CoachApp
import com.guitarcoach.app.ui.theme.GuitarCoachTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer(applicationContext)
        setContent {
            GuitarCoachTheme {
                CoachApp(container)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::container.isInitialized) container.shutdown()
    }
}
