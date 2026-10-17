package com.example.hevygroovy.ui

import com.example.hevygroovy.dto.TemplateCard
import java.util.Locale

enum class TemplateSort(val label: String) {
    OldestCompleted("Oldest completed first"),
    NewestCompleted("Newest completed first"),
    Name("Name A–Z"),
}

fun sortedTemplates(templates: List<TemplateCard>, completedAt: Map<Long, Long>, sort: TemplateSort): List<TemplateCard> {
    val names = compareBy<TemplateCard> { it.title.lowercase(Locale.ROOT) }.thenBy { it.id }
    val comparator = when (sort) {
        TemplateSort.OldestCompleted -> compareBy<TemplateCard> { completedAt[it.id] ?: Long.MIN_VALUE }.then(names)
        TemplateSort.NewestCompleted -> compareByDescending<TemplateCard> { completedAt[it.id] ?: Long.MIN_VALUE }.then(names)
        TemplateSort.Name -> names
    }
    return templates.sortedWith(comparator)
}
