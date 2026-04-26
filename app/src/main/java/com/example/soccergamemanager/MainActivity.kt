package com.example.soccergamemanager

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.soccergamemanager.ui.MainViewModel
import com.example.soccergamemanager.ui.MainViewModelFactory
import com.example.soccergamemanager.ui.OrientationLockMode
import com.example.soccergamemanager.ui.SoccerManagerRoot
import com.example.soccergamemanager.ui.theme.SoccerGameManagerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            repository = (application as SoccerManagerApp).container.repository,
            settingsStore = (application as SoccerManagerApp).container.settingsStore,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                requestedOrientation = when (state.orientationLockMode) {
                    OrientationLockMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    OrientationLockMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    OrientationLockMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
        }
        setContent {
            SoccerGameManagerTheme {
                SoccerManagerRoot(viewModel = viewModel)
            }
        }
    }
}
