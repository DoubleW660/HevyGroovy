package com.example.hevygroovy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.hevygroovy.ui.WorkoutHomeViewModel
import com.example.hevygroovy.ui.WorkoutApp
import com.example.hevygroovy.ui.LibraryViewModel
import com.example.hevygroovy.ui.theme.HevyGroovyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HevyGroovyApplication).container
        val viewModel = ViewModelProvider(this, WorkoutHomeViewModel.Factory(container))
            .get(WorkoutHomeViewModel::class.java)
        val library = ViewModelProvider(this, LibraryViewModel.Factory(container)).get(LibraryViewModel::class.java)
        setContent {
            HevyGroovyTheme {
                WorkoutApp(viewModel, library)
            }
        }
    }
}

