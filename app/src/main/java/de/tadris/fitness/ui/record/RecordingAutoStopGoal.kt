/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.record

import de.tadris.fitness.data.CompletionRule

/** A sensor-agnostic goal evaluated from the recorder's live aggregate values. */
data class RecordingAutoStopGoal(
    val completionRule: CompletionRule,
    val durationMillis: Long? = null,
    val distanceMeters: Double? = null,
    val repetitions: Int? = null,
) {
    fun isReached(
        activeDurationMillis: Long,
        recordedDistanceMeters: Double,
        recordedRepetitions: Int,
    ): Boolean = when (completionRule) {
        CompletionRule.USER_CONFIRM -> false
        CompletionRule.DURATION_REACHED ->
            durationMillis?.takeIf { it > 0 }?.let { activeDurationMillis >= it } == true

        CompletionRule.METRIC_TARGET_REACHED -> when {
            distanceMeters != null && distanceMeters > 0 -> recordedDistanceMeters >= distanceMeters
            repetitions != null && repetitions > 0 -> recordedRepetitions >= repetitions
            durationMillis != null && durationMillis > 0 -> activeDurationMillis >= durationMillis
            else -> false
        }
    }
}
