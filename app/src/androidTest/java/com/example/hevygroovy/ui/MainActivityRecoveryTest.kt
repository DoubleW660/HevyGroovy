package com.example.hevygroovy.ui

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hevygroovy.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityRecoveryTest {
    @Test
    fun realActivityLoadsAndRetainsViewModelAcrossRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: WorkoutHomeViewModel? = null
            var loading = true
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
            while (loading && System.nanoTime() < deadline) {
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[WorkoutHomeViewModel::class.java]
                    loading = model!!.state.loading
                }
                if (loading) Thread.sleep(25)
            }
            scenario.onActivity {
                assertNotNull(model)
                assertFalse(model!!.state.loading)
                assertTrue(model!!.state.loaded)
                assertNull(model!!.state.error)
            }
            val original = model
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = ViewModelProvider(activity)[WorkoutHomeViewModel::class.java]
                assertSame(original, restored)
                assertTrue(restored.state.loaded)
            }
        }
    }
}
