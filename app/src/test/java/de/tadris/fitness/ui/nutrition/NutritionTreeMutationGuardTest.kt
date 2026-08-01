/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.nutrition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTreeMutationGuardTest {

    @Test
    fun onlyOneEditorMutationCanBeAcquiredAtATime() {
        assertTrue(NutritionTreeMutationGuard.tryAcquire())
        try {
            assertTrue(NutritionTreeMutationGuard.inProgress.value)
            assertFalse(NutritionTreeMutationGuard.tryAcquire())
        } finally {
            NutritionTreeMutationGuard.release()
        }
        assertFalse(NutritionTreeMutationGuard.inProgress.value)
        assertTrue(NutritionTreeMutationGuard.tryAcquire())
        NutritionTreeMutationGuard.release()
    }
}
