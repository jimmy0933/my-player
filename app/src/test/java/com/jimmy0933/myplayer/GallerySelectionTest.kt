package com.jimmy0933.myplayer

import org.junit.Assert.*
import org.junit.Test

class GallerySelectionTest {
    @Test fun longPressStartsSelectionAndCheckboxesAreHiddenBeforeIt() {
        val initial = emptySet<String>()
        assertFalse(GallerySelection.active(initial))
        val afterLongPress = GallerySelection.toggle(initial, "photo:1")
        assertTrue(GallerySelection.active(afterLongPress))
        assertEquals(setOf("photo:1"), afterLongPress)
    }

    @Test fun tappingInSelectionModeChangesOnlySelection() {
        val one = setOf("photo:1")
        val two = GallerySelection.toggle(one, "video:2")
        assertEquals(setOf("photo:1", "video:2"), two)
        assertEquals(setOf("photo:1"), GallerySelection.toggle(two, "video:2"))
    }

    @Test fun uncheckingLastItemExitsSelectionMode() {
        val next = GallerySelection.toggle(setOf("photo:1"), "photo:1")
        assertTrue(next.isEmpty())
        assertFalse(GallerySelection.active(next))
    }
}
