package com.jimmy0933.myplayer

import org.junit.Assert.*
import org.junit.Test

class PlayerMathTest {
    @Test fun playbackSeekAndTime() {
        assertEquals("00:00", PlayerMath.durationLabel(0))
        assertEquals("01:05", PlayerMath.durationLabel(65000))
        assertEquals("1:01:01", PlayerMath.durationLabel(3661000))
        assertEquals(16500L, PlayerMath.seek(10000, 100f, 100000))
        assertEquals(0L, PlayerMath.seek(10000, -200f, 100000))
        assertEquals(100000L, PlayerMath.seek(95000, 9999f, 100000))
    }
    @Test fun gestureAndVideoNavigation() {
        assertTrue(PlayerMath.centerTap(500f, 500f, 1000f, 1000f))
        assertFalse(PlayerMath.centerTap(500f, 950f, 1000f, 1000f))
        assertFalse(PlayerMath.centerTap(50f, 500f, 1000f, 1000f))
        assertEquals(3, PlayerMath.videoNeighbor(listOf(false, true, false, true), 1, 1))
        assertEquals(1, PlayerMath.videoNeighbor(listOf(false, true, false, true), 3, -1))
        assertNull(PlayerMath.videoNeighbor(listOf(false, true, false, true), 3, 1))
    }
    @Test fun doubleTapSeekZonesAndVideoBounds() {
        assertEquals(-1, PlayerMath.doubleTapSeekSide(10f, 1000f))
        assertEquals(-1, PlayerMath.doubleTapSeekSide(299f, 1000f))
        assertEquals(0, PlayerMath.doubleTapSeekSide(300f, 1000f))
        assertEquals(0, PlayerMath.doubleTapSeekSide(500f, 1000f))
        assertEquals(1, PlayerMath.doubleTapSeekSide(700f, 1000f))
        assertEquals(1, PlayerMath.doubleTapSeekSide(999f, 1000f))
        assertEquals(0, PlayerMath.doubleTapSeekSide(-1f, 1000f))
        assertEquals(0, PlayerMath.doubleTapSeekSide(5f, 0f))
        assertEquals(0, PlayerMath.doubleTapSeekSide(Float.NaN, 1000f))
        assertEquals(20_000L, PlayerMath.doubleTapSeekPosition(30_000L, -1, 90_000L))
        assertEquals(40_000L, PlayerMath.doubleTapSeekPosition(30_000L, 1, 90_000L))
        assertEquals(0L, PlayerMath.doubleTapSeekPosition(4_000L, -1, 90_000L))
        assertEquals(90_000L, PlayerMath.doubleTapSeekPosition(86_000L, 1, 90_000L))
        assertEquals(10_000L, PlayerMath.doubleTapSeekPosition(0L, 1, 0L))
        assertEquals(30_000L, PlayerMath.doubleTapSeekPosition(30_000L, 0, 90_000L))
    }

    @Test fun progressAutoHideDelayOptions() {
        assertEquals(listOf(1, 2, 3, 5, 10, 15, 30, 0), PlayerMath.progressHideDelayOptions)
        assertEquals(5, PlayerMath.progressHideDelaySeconds(-1))
        assertEquals(5, PlayerMath.progressHideDelaySeconds(999))
        assertEquals(5, PlayerMath.progressHideDelaySeconds(5))
        assertEquals(1, PlayerMath.progressHideDelaySeconds(1))
        assertEquals(2, PlayerMath.progressHideDelaySeconds(2))
        assertEquals(30, PlayerMath.progressHideDelaySeconds(30))
        assertEquals(0, PlayerMath.progressHideDelaySeconds(0))
    }

    @Test fun lastPlayedHoverOnlyHighlightsLatestVideo() {
        assertTrue(PlayerMath.isLastPlayedVideo("video:two", true, "video:two"))
        assertFalse(PlayerMath.isLastPlayedVideo("video:one", true, "video:two"))
        assertFalse(PlayerMath.isLastPlayedVideo("video:two", false, "video:two"))
        assertFalse(PlayerMath.isLastPlayedVideo("video:two", true, null))
    }

    @Test fun playbackResumeAndCompletedVideoRestart() {
        assertEquals(45_000L, PlayerMath.resumePosition(45_000L, 120_000L))
        assertEquals(0L, PlayerMath.resumePosition(118_000L, 120_000L))
        assertEquals(0L, PlayerMath.resumePosition(120_000L, 120_000L))
        assertEquals(0L, PlayerMath.resumePosition(-100L, 120_000L))
        assertEquals(5_000L, PlayerMath.resumePosition(5_000L, 0L))
    }

    @Test fun settingsBoundaries() {
        assertEquals(.8f, PlayerMath.progressScale(0f))
        assertEquals(2.5f, PlayerMath.progressScale(5f))
        assertEquals(.15f, PlayerMath.progressOpacity(0f))
        assertEquals(1f, PlayerMath.progressOpacity(5f))
        assertEquals(.72f, PlayerMath.progressOpacity(Float.NaN))
        assertEquals(2f, PlayerMath.progressThicknessDp(.8f), .001f)
        assertEquals(14f, PlayerMath.progressThicknessDp(2.5f), .001f)
        assertTrue(PlayerMath.progressThicknessDp(2f) >
            PlayerMath.progressThicknessDp(1f))
    }
    @Test fun mediaStoreFolderSafety() {
        assertEquals("Movies/MyPlayer/", MediaOperations.cleanFolder("", true))
        assertEquals("Pictures/MyPlayer/", MediaOperations.cleanFolder("", false))
        assertEquals("Movies/Kids/", MediaOperations.cleanFolder("Movies/Kids", true))
        assertEquals("Movies/Kids/", MediaOperations.cleanFolder("Movies/../Kids", true))
        // Android MediaStore.Video.Media rejects Download/ as a primary directory.
        assertEquals("Movies/Download/", MediaOperations.cleanFolder("Download", true))
        assertEquals("Movies/Download/Clips/", MediaOperations.cleanFolder("Download/Clips", true))
        assertEquals("Pictures/Download/", MediaOperations.cleanFolder("Download", false))
        assertEquals("Pictures/Movies/Vacation/", MediaOperations.cleanFolder("Movies/Vacation", false))
        assertEquals("DCIM/Camera/", MediaOperations.cleanFolder("dcim/Camera", true))
        assertEquals("Pictures/", MediaOperations.cleanFolder("Pictures", false))
    }
}
