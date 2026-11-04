package com.jimmy0933.myplayer

/**
 * An Android MediaStore folder browser. It starts at the indexed-media storage
 * root and displays only the *immediate* subfolders, rather than flattening
 * every DCIM/Pictures/Movies descendant into a single list.
 *
 * All paths are RELATIVE_PATH values. This is not a raw filesystem explorer,
 * and inaccessible/non-media directories are intentionally not invented.
 */
object FolderNavigation {
    data class Child(val path: String, val name: String, val mediaCount: Int)

    fun normalize(raw: String): String = raw.replace('\\', '/').split('/')
        .filter { it.isNotBlank() && it != "." && it != ".." }
        .joinToString("/")

    fun parent(raw: String): String = normalize(raw).substringBeforeLast('/', "")

    fun isDirect(mediaPath: String, currentFolder: String): Boolean =
        normalize(mediaPath) == normalize(currentFolder)

    fun children(mediaPaths: List<String>, currentFolder: String): List<Child> {
        val current = normalize(currentFolder)
        val prefix = if (current.isEmpty()) "" else "$current/"
        val counts = mutableMapOf<String, Int>()
        for (raw in mediaPaths) {
            val path = normalize(raw)
            if (!path.startsWith(prefix)) continue
            val remainder = path.removePrefix(prefix)
            if (remainder.isEmpty()) continue
            val name = remainder.substringBefore('/')
            if (name.isEmpty()) continue
            val childPath = prefix + name
            counts[childPath] = (counts[childPath] ?: 0) + 1
        }
        return counts.map { (path, count) ->
            Child(path, path.substringAfterLast('/'), count)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
