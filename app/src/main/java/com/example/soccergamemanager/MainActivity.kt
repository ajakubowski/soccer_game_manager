package com.example.soccergamemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.soccergamemanager.ui.MainViewModel
import com.example.soccergamemanager.ui.MainViewModelFactory
import com.example.soccergamemanager.ui.SoccerManagerRoot
import com.example.soccergamemanager.ui.theme.SoccerGameManagerTheme

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
        setContent {
            SoccerGameManagerTheme {
                SoccerManagerRoot(viewModel = viewModel)
            }
        }
    }
}
