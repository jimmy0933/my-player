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
    @Test fun settingsBoundaries() {
        assertEquals(.8f, PlayerMath.progressScale(0f))
        assertEquals(2.5f, PlayerMath.progressScale(5f))
        assertEquals(.96f, PlayerMath.progressWidth(Float.NaN))
        assertEquals(.6f, PlayerMath.progressWidth(.2f))
        assertEquals(.15f, PlayerMath.progressOpacity(0f))
        assertEquals(1f, PlayerMath.progressOpacity(5f))
        assertEquals(.72f, PlayerMath.progressOpacity(Float.NaN))
    }
    @Test fun mediaStoreFolderSafety() {
        assertEquals("Movies/MyPlayer/", MediaOperations.cleanFolder("", true))
        assertEquals("Pictures/MyPlayer/", MediaOperations.cleanFolder("", false))
        assertEquals("Movies/Kids/", MediaOperations.cleanFolder("Movies/Kids", true))
        assertEquals("Movies/Kids/", MediaOperations.cleanFolder("Movies/../Kids", true))
    }
}
