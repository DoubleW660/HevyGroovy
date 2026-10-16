package com.example.hevygroovy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.ReportDrawnWhen
import androidx.lifecycle.ViewModelProvider
import com.example.hevygroovy.ui.WorkoutHomeViewModel
import com.example.hevygroovy.ui.WorkoutApp
import com.example.hevygroovy.ui.LibraryViewModel
import com.example.hevygroovy.ui.UiPerformanceTiming
import com.example.hevygroovy.ui.theme.HevyGroovyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HevyGroovyApplication).container
        val viewModel = ViewModelProvider(this, WorkoutHomeViewModel.Factory(container.workouts, UiPerformanceTiming.android()))
            .get(WorkoutHomeViewModel::class.java)
        val library = ViewModelProvider(this, LibraryViewModel.Factory(container.library, UiPerformanceTiming.android())).get(LibraryViewModel::class.java)
        setContent {
            HevyGroovyTheme {
                ReportDrawnWhen { viewModel.state.loaded && !viewModel.state.busy && viewModel.state.error == null }
                WorkoutApp(viewModel, library)
            }
        }
    }
}

