package com.example.hevygroovy.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.hevygroovy.app.ProfileGateway
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProfileViewModelTest {
    @Test
    fun loadIsExplicitAndRepeatedRefreshesAreIgnoredWhileLoading() {
        val gateway = FakeGateway()
        val model = ProfileViewModel(gateway)
        assertTrue(gateway.calls.isEmpty())
        assertFalse(model.state.loaded)
        model.refresh(); model.refresh()
        assertEquals(listOf("load"), gateway.calls)
        assertTrue(model.state.loading)
        gateway.succeed(WeightUnit.KG)
        assertTrue(model.state.loaded)
        assertFalse(model.state.loading)
        assertEquals(WeightUnit.KG, model.state.unit)
    }

    @Test
    fun failedInitialLoadCanBeRetriedAndDoesNotAllowSavingAnUnknownPreference() {
        val gateway = FakeGateway()
        val model = ProfileViewModel(gateway)
        model.setDefaultUnit(WeightUnit.KG)
        assertTrue(gateway.calls.isEmpty())
        model.refresh(); gateway.fail()
        assertFalse(model.state.loaded)
        assertFalse(model.state.loading)
        assertNotNull(model.state.error)
        model.setDefaultUnit(WeightUnit.KG)
        model.retry(); gateway.succeed(WeightUnit.LB)
        assertEquals(listOf("load", "load"), gateway.calls)
        assertNull(model.state.error)
        assertTrue(model.state.loaded)
    }

    @Test
    fun savingWaitsForPersistenceAndIgnoresCurrentUnitAndDuplicateTaps() {
        val gateway = FakeGateway()
        val model = ProfileViewModel(gateway)
        model.refresh(); gateway.succeed(WeightUnit.LB)
        model.setDefaultUnit(WeightUnit.LB)
        model.setDefaultUnit(WeightUnit.KG)
        model.setDefaultUnit(WeightUnit.KG)
        model.refresh()
        assertEquals(listOf("load", "save:KG"), gateway.calls)
        assertEquals(WeightUnit.LB, model.state.unit)
        assertTrue(model.state.loading)
        gateway.succeed(WeightUnit.KG)
        assertEquals(WeightUnit.KG, model.state.unit)
        assertFalse(model.state.loading)
        assertNull(model.state.error)
    }

    @Test
    fun failedSaveKeepsPreviousUnitAndRetrySavesTheRequestedUnit() {
        val gateway = FakeGateway()
        val model = ProfileViewModel(gateway)
        model.refresh(); gateway.succeed(WeightUnit.LB)
        model.setDefaultUnit(WeightUnit.KG); gateway.fail()
        assertEquals(WeightUnit.LB, model.state.unit)
        assertNotNull(model.state.error)
        assertTrue(model.state.loaded)
        model.retry(); gateway.succeed(WeightUnit.KG)
        assertEquals(listOf("load", "save:KG", "save:KG"), gateway.calls)
        assertEquals(WeightUnit.KG, model.state.unit)
        assertNull(model.state.error)
    }

    @Test
    fun failedRefreshPreservesTheLastLoadedPreference() {
        val gateway = FakeGateway()
        val model = ProfileViewModel(gateway)
        model.refresh(); gateway.succeed(WeightUnit.KG)
        model.refresh(); gateway.fail()
        assertTrue(model.state.loaded)
        assertEquals(WeightUnit.KG, model.state.unit)
        model.retry(); gateway.succeed(WeightUnit.KG)
        assertEquals(listOf("load", "load", "load"), gateway.calls)
        assertNull(model.state.error)
    }

    @Test
    fun clearedViewModelIgnoresLateCallbacksAndRequests() {
        val gateway = FakeGateway()
        val store = ViewModelStore()
        val model = ViewModelProvider(store, ProfileViewModel.Factory(gateway))[ProfileViewModel::class.java]
        model.refresh()
        store.clear()
        gateway.succeed(WeightUnit.KG)
        assertFalse(model.state.loaded)
        model.refresh(); model.setDefaultUnit(WeightUnit.KG)
        assertEquals(listOf("load"), gateway.calls)
    }

    private class FakeGateway : ProfileGateway {
        val calls = mutableListOf<String>()
        private lateinit var callback: ProfileGateway.Callback

        override fun load(callback: ProfileGateway.Callback) {
            calls += "load"
            this.callback = callback
        }

        override fun setDefaultUnit(unit: WeightUnit, callback: ProfileGateway.Callback) {
            calls += "save:${unit.name}"
            this.callback = callback
        }

        fun succeed(unit: WeightUnit) = callback.onSuccess(unit)
        fun fail() = callback.onFailure(IllegalStateException("Test failure"))
    }
}
