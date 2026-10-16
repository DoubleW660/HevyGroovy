package com.example.hevygroovy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hevygroovy.app.ProfileGateway
import com.example.hevygroovy.entity.enums.Unit as WeightUnit

data class ProfileState(
    val unit: WeightUnit = WeightUnit.LB,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel(private val gateway: ProfileGateway) : ViewModel() {
    var state by mutableStateOf(ProfileState())
        private set

    private var cleared = false
    private var failedUnit: WeightUnit? = null

    fun refresh() {
        if (cleared || state.loading) return
        failedUnit = null
        execute(null) { gateway.load(it) }
    }

    fun setDefaultUnit(unit: WeightUnit) {
        if (cleared || state.loading || !state.loaded || state.unit == unit) return
        failedUnit = null
        execute(unit) { gateway.setDefaultUnit(unit, it) }
    }

    fun retry() {
        val unit = failedUnit
        if (unit == null) refresh() else setDefaultUnit(unit)
    }

    private fun execute(savingUnit: WeightUnit?, operation: (ProfileGateway.Callback) -> Unit) {
        state = state.copy(loading = true, error = null)
        operation(object : ProfileGateway.Callback {
            override fun onSuccess(unit: WeightUnit) {
                if (cleared) return
                failedUnit = null
                state = ProfileState(unit = unit, loaded = true)
            }

            override fun onFailure(error: Exception) {
                if (cleared) return
                failedUnit = savingUnit
                state = state.copy(loading = false, error = if (savingUnit == null)
                    "Couldn't load your preferences. Please try again."
                    else "Couldn't save your weight unit. Please try again.")
            }
        })
    }

    override fun onCleared() { cleared = true }

    class Factory(private val gateway: ProfileGateway) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ProfileViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(gateway) as T
        }
    }
}
