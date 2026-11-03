package com.jimmy0933.myplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalMedia(
    val uri: Uri,
    val id: Long,
    val name: String,
    val mime: String,
    val size: Long,
    val dateAdded: Long,
    val durationMs: Long,
    val relativePath: String,
    val isVideo: Boolean
) {
    val folder: String get() = relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "其他" }
    val key: String get() = uri.toString()
}

object MediaLibrary {
    suspend fun load(context: Context): List<LocalMedia> = withContext(Dispatchers.IO) {
        val all = mutableListOf<LocalMedia>()
        val resolver = context.contentResolver
        for (video in listOf(true, false)) {
            val collection = if (video)
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (video) MediaStore.Video.VideoColumns.DURATION else MediaStore.Images.ImageColumns.DATE_TAKEN
            )
            try {
                resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dateIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val folderIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val durationIndex = if (video) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                        val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
                        val path = if (folderIndex >= 0) cursor.getString(folderIndex).orEmpty() else ""
                        val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                        val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                        val duration = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L
                        all += LocalMedia(ContentUris.withAppendedId(collection, id), id,
                            name.ifBlank { if (video) "影片 $id" else "照片 $id" }, mime,
                            size.coerceAtLeast(0), date, duration.coerceAtLeast(0), path, video)
                    }
                }
            } catch (_: SecurityException) {
                // Android's partial-photo access can be narrower than a full library scan.
            } catch (_: Exception) {
                // Keep the other media category available if a provider fails.
            }
        }
        all.distinctBy { it.key }.sortedWith(compareByDescending<LocalMedia> { it.dateAdded }.thenByDescending { it.id })
    }

    fun folders(media: List<LocalMedia>): List<String> =
        media.map { it.relativePath.trimEnd('/') }.filter { it.isNotEmpty() }.distinct().sorted()
}

object PlayerMath {
    fun durationLabel(milliseconds: Long): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1000
        return if (seconds >= 3600)
            "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%02d:%02d".format(seconds / 60, seconds % 60)
    }
    fun seek(base: Long, deltaPx: Float, duration: Long): Long {
        if (duration <= 0) return base.coerceAtLeast(0)
        return (base + (deltaPx * 65f).toLong()).coerceIn(0, duration)
    }
    fun centerTap(x: Float, y: Float, width: Float, height: Float): Boolean =
        width > 0 && height > 0 && x in (width * .25f)..(width * .75f) &&
            y in (height * .28f)..(height * .64f)
    fun videoNeighbor(videoIndices: List<Boolean>, index: Int, step: Int): Int? {
        if (index !in videoIndices.indices || step == 0) return null
        var p = index + if (step > 0) 1 else -1
        while (p in videoIndices.indices) {
            if (videoIndices[p]) return p
            p += if (step > 0) 1 else -1
        }
        return null
    }
    fun progressScale(value: Float): Float = if (value.isFinite()) value.coerceIn(.8f, 2.5f) else 1f
    fun progressWidth(value: Float): Float = if (value.isFinite()) value.coerceIn(.6f, 1f) else .96f
}
