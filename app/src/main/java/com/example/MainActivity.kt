package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.TravelNavHost
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TravelViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: TravelViewModel by viewModels { TravelViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install official AndroidX SplashScreen before super.onCreate()
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TravelNavHost(viewModel = viewModel)
                }
            }
        }
    }
}
