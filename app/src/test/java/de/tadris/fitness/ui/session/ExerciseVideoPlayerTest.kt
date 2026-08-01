/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.session

import de.tadris.fitness.data.isExerciseHlsVideoUrl
import de.tadris.fitness.data.isSupportedExerciseVideoUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseVideoPlayerTest {

    @Test
    fun `only public absolute https video URLs are accepted`() {
        assertTrue(isSupportedExerciseVideoUrl("https://cdn.example.org/push-up.mp4"))
        assertTrue(isSupportedExerciseVideoUrl(" https://8.8.8.8/video.webm "))

        assertFalse(isSupportedExerciseVideoUrl(""))
        assertFalse(isSupportedExerciseVideoUrl("video.mp4"))
        assertFalse(isSupportedExerciseVideoUrl("http://media.example.org/video.webm"))
        assertFalse(isSupportedExerciseVideoUrl("file:///data/video.mp4"))
        assertFalse(isSupportedExerciseVideoUrl("content://media/video/1"))
        assertFalse(isSupportedExerciseVideoUrl("https://"))
        assertFalse(isSupportedExerciseVideoUrl("not a URL"))
        assertFalse(isSupportedExerciseVideoUrl("https://user:secret@example.org/video.mp4"))
        assertFalse(isSupportedExerciseVideoUrl("https://www.youtube.com/watch?v=exercise"))
        assertFalse(isSupportedExerciseVideoUrl("https://example.org/exercise"))
    }

    @Test
    fun `local private and ambiguous numeric hosts are rejected`() {
        listOf(
            "https://localhost/video.mp4",
            "https://media.localhost/video.mp4",
            "https://127.0.0.1/video.mp4",
            "https://10.1.2.3/video.mp4",
            "https://172.16.4.5/video.mp4",
            "https://192.168.1.2/video.mp4",
            "https://169.254.1.2/video.mp4",
            "https://2130706433/video.mp4",
            "https://0177.0.0.1/video.mp4",
            "https://[::1]/video.mp4",
            "https://[fe80::1]/video.mp4",
            "https://[fc00::1]/video.mp4",
        ).forEach { url ->
            assertFalse("Expected URL to be rejected: $url", isSupportedExerciseVideoUrl(url))
        }
    }

    @Test
    fun `HLS classification ignores case and query parameters`() {
        assertTrue(isExerciseHlsVideoUrl("https://cdn.example.org/live/stream.M3U8?token=abc"))
        assertFalse(isExerciseHlsVideoUrl("https://cdn.example.org/video.mp4?next=.m3u8"))
        assertFalse(isExerciseHlsVideoUrl("http://cdn.example.org/live/stream.m3u8"))
        assertFalse(isExerciseHlsVideoUrl("https://127.0.0.1/live/stream.m3u8"))
    }
}
