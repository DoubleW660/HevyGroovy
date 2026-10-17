package com.example.hevygroovy.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.hevygroovy.entity.enums.Unit as WeightUnit

/** Aggregates use KG internally; presentation follows the local profile. */
internal val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.KG }

internal fun Double.inWeightUnit(unit: WeightUnit): Double =
    if (unit == WeightUnit.LB) this / 0.45359237 else this

internal val WeightUnit.symbol: String get() = name.lowercase()
