/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareFileHelpersTest {

    @Test
    fun `explicit mime type wins and blank values fall back safely`() {
        assertEquals("text/plain", resolveShareMimeType(" text/plain ", "application/json"))
        assertEquals("application/json", resolveShareMimeType(" ", " application/json "))
        assertEquals("application/octet-stream", resolveShareMimeType(null, ""))
    }

    @Test
    fun `provider display name wins over uri path`() {
        assertEquals(
            "program.json",
            resolveShareFileName("program.json", "share/fallback.fitregimen.json"),
        )
    }

    @Test
    fun `file name strips paths and rejects empty navigation segments`() {
        assertEquals("program.json", resolveShareFileName(null, "share/program.json"))
        assertEquals("backup.zip", resolveShareFileName("folder\\backup.zip", null))
        assertEquals(DEFAULT_SHARE_FILE_NAME, resolveShareFileName("..", null))
        assertEquals(DEFAULT_SHARE_FILE_NAME, resolveShareFileName(null, " "))
    }
}
