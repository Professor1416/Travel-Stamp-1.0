package com.example

import android.content.Intent
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
import com.example.data.notification.ReminderNavigationRequest
import com.example.data.notification.TripNotificationHelper
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

        if (savedInstanceState == null) {
            handleReminderIntent(intent)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TravelNavHost(viewModel = viewModel)
                }
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderIntent(intent)
    }

    private fun handleReminderIntent(intent: Intent?) {
        val request = ReminderNavigationRequest.fromIntent(intent)
        if (request != null) {
            viewModel.onReminderNavigationRequested(request.tripId)
            intent?.removeExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER)
        }
    }
}
