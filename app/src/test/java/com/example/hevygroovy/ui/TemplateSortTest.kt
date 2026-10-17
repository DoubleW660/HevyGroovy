package com.example.hevygroovy.ui

import androidx.lifecycle.SavedStateHandle
import com.example.hevygroovy.app.LibraryGateway
import com.example.hevygroovy.dto.TemplateCard
import com.example.hevygroovy.model.TemplateSummaryModel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class TemplateSortTest {
    private val templates = listOf(card(1, "Upper"), card(2, "Lower"), card(3, "New routine"), card(4, "Another routine"))
    private val completions = mapOf(1L to 9000L, 2L to 3000L)

    @Test fun oldestFirstPrioritizesNeverCompletedThenLeastRecentlyCompleted() {
        assertEquals(listOf(4L, 3L, 2L, 1L), sortedTemplates(templates, completions, TemplateSort.OldestCompleted).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), templates.map { it.id })
    }

    @Test fun newestFirstAndNameHaveDeterministicTiesAndPutNeverCompletedAtTheEndOfNewest() {
        assertEquals(listOf(1L, 2L, 4L, 3L), sortedTemplates(templates, completions, TemplateSort.NewestCompleted).map { it.id })
        assertEquals(listOf(4L, 2L, 3L, 1L), sortedTemplates(templates, completions, TemplateSort.Name).map { it.id })
        val same = listOf(card(2, "bench"), card(1, "Bench"))
        assertEquals(listOf(1L, 2L), sortedTemplates(same, mapOf(1L to 10L, 2L to 10L), TemplateSort.OldestCompleted).map { it.id })
    }

    @Test fun sortChoiceSurvivesRecreationAndDefaultsToOldest() {
        val handle = SavedStateHandle()
        val gateway = mock(LibraryGateway::class.java)
        val model = LibraryViewModel(gateway, handle)
        assertEquals(TemplateSort.OldestCompleted, model.templateSort)
        model.selectTemplateSort(TemplateSort.Name)
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        assertEquals(TemplateSort.Name, LibraryViewModel(gateway, restored).templateSort)
    }

    private fun card(id: Long, title: String) = TemplateCard(TemplateSummaryModel().apply {
        templateWorkoutId = id; this.title = title
    })
}
