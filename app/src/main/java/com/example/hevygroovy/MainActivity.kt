package com.example.hevygroovy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.hevygroovy.ui.WorkoutHomeViewModel
import com.example.hevygroovy.ui.WorkoutHomeScreen
import com.example.hevygroovy.ui.theme.HevyGroovyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HevyGroovyApplication).container
        val viewModel = ViewModelProvider(this, WorkoutHomeViewModel.Factory(container))
            .get(WorkoutHomeViewModel::class.java)
        setContent {
            HevyGroovyTheme {
                WorkoutHomeScreen(viewModel)
            }
        }
    }
}

