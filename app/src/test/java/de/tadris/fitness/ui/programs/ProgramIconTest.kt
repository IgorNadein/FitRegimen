package de.tadris.fitness.ui.programs

import de.tadris.fitness.util.Icon
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramIconTest {

    @Test
    fun `known logical icon name is preserved`() {
        assertEquals(Icon.RUNNING, resolveProgramIcon(Icon.RUNNING.name))
    }

    @Test
    fun `missing and unknown icon names use neutral activity fallback`() {
        assertEquals(Icon.ACTIVITY, resolveProgramIcon(null))
        assertEquals(Icon.ACTIVITY, resolveProgramIcon("removed-icon"))
    }
}
