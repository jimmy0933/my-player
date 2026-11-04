package com.jimmy0933.myplayer

import org.junit.Assert.*
import org.junit.Test

class FolderNavigationTest {
    private val indexedPaths = listOf(
        "DCIM/Camera/", "DCIM/Camera/", "DCIM/Screenshots/",
        "Pictures/LINE/", "Pictures/LINE/Albums/", "Movies/",
        "Pictures/LINE/Albums/", "Pictures/", ""
    )

    @Test fun rootShowsOnlyFirstLevelFoldersWithDescendantCounts() {
        val children = FolderNavigation.children(indexedPaths, "")
        assertEquals(listOf("DCIM", "Movies", "Pictures"), children.map { it.name })
        assertEquals(listOf(3, 1, 4), children.map { it.mediaCount })
        assertTrue(children.none { it.name == "Camera" || it.name == "LINE" })
    }

    @Test fun drillingDownShowsOnlyNextLevelFolders() {
        val dcim = FolderNavigation.children(indexedPaths, "DCIM/")
        assertEquals(listOf("Camera", "Screenshots"), dcim.map { it.name })
        assertEquals(listOf(2, 1), dcim.map { it.mediaCount })
        val pictures = FolderNavigation.children(indexedPaths, "Pictures")
        assertEquals(listOf("LINE"), pictures.map { it.name })
        assertEquals(3, pictures.single().mediaCount)
        assertEquals(listOf("Albums"), FolderNavigation.children(indexedPaths, "Pictures/LINE").map { it.name })
    }

    @Test fun directMediaAndParentAreCorrect() {
        assertTrue(FolderNavigation.isDirect("DCIM/Camera/", "DCIM/Camera"))
        assertFalse(FolderNavigation.isDirect("DCIM/Camera/", "DCIM"))
        assertTrue(FolderNavigation.isDirect("", ""))
        assertEquals("DCIM", FolderNavigation.parent("DCIM/Camera/"))
        assertEquals("", FolderNavigation.parent("DCIM"))
        assertEquals("", FolderNavigation.parent(""))
    }

    @Test fun normalizationDoesNotCreateParentTraversal() {
        assertEquals("DCIM/Camera", FolderNavigation.normalize("/DCIM/./Camera//"))
        assertEquals("DCIM/Camera", FolderNavigation.normalize("DCIM/../Camera"))
        assertEquals("Pictures/LINE", FolderNavigation.normalize("Pictures\\LINE\\"))
        assertTrue(FolderNavigation.children(emptyList(), "").isEmpty())
    }
}
