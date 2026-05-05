package com.bplogger.app.domain.model

import androidx.compose.ui.graphics.Color

enum class BpCategory(val label: String, val color: Color) {
    NORMAL("Normal", Color(0xFF388E3C)),
    ELEVATED("Elevated", Color(0xFFFBC02D)),
    HIGH_STAGE_1("High Stage 1", Color(0xFFE65100)),
    HIGH_STAGE_2("High Stage 2", Color(0xFFD32F2F)),
    CRISIS("Hypertensive Crisis", Color(0xFF880E4F))
}

object BpClassifier {
    fun classify(systolic: Int, diastolic: Int): BpCategory {
        return when {
            systolic > 180 || diastolic > 120 -> BpCategory.CRISIS
            systolic >= 140 || diastolic >= 90 -> BpCategory.HIGH_STAGE_2
            systolic in 130..139 || diastolic in 80..89 -> BpCategory.HIGH_STAGE_1
            systolic in 120..129 && diastolic < 80 -> BpCategory.ELEVATED
            else -> BpCategory.NORMAL
        }
    }

    fun isHighAlert(systolic: Int, diastolic: Int): Boolean {
        val cat = classify(systolic, diastolic)
        return cat == BpCategory.HIGH_STAGE_2 || cat == BpCategory.CRISIS
    }
}
