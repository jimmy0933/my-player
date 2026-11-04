package com.jimmy0933.myplayer

/**
 * A normal tap opens media until a long press enters selection mode.
 * Selection mode stays visible only while at least one item is checked.
 */
object GallerySelection {
    fun active(selected: Set<String>): Boolean = selected.isNotEmpty()

    fun toggle(selected: Set<String>, key: String): Set<String> =
        if (key in selected) selected - key else selected + key
}
