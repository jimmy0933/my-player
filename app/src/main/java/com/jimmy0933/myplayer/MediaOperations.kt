package com.jimmy0933.myplayer

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

object MediaOperations {
    data class Copied(val source: LocalMedia, val destination: Uri)
    data class Result(val copied: List<Copied>, val failures: List<String>)
    data class MoveResult(val moved: List<LocalMedia>, val failures: List<String>)

    /**
     * Relocate the existing MediaStore item instead of making a full-sized
     * duplicate first. Call only after createWriteRequest approval for
     * non-owned media. On failure the original stays in place.
     */
    suspend fun move(context: Context, entries: List<LocalMedia>, folder: String): MoveResult =
        withContext(Dispatchers.IO) {
            val moved = mutableListOf<LocalMedia>()
            val failures = mutableListOf<String>()
            val resolver = context.contentResolver
            for (item in entries) {
                try {
                    val destination = cleanFolder(folder, item.isVideo)
                    if (item.relativePath.trimEnd('/') == destination.trimEnd('/')) {
                        moved += item
                        continue
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, destination)
                    }
                    val updated = resolver.update(item.uri, values, null, null)
                    check(updated == 1) { "系統未允許更新檔案位置" }
                    // Verify actual location rather than reporting success on a
                    // provider that silently ignores RELATIVE_PATH.
                    val storedPath = resolver.query(
                        item.uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                    check(storedPath?.trimEnd('/') == destination.trimEnd('/')) {
                        "檔案位置未更新，請檢查資料夾是否支援此媒體格式"
                    }
                    moved += item
                } catch (e: Exception) {
                    failures += "${item.name}：${e.message ?: e.javaClass.simpleName}"
                }
            }
            MoveResult(moved, failures)
        }

    /** Copy into an in-app chosen MediaStore folder, never launch an empty external SAF picker. */
    suspend fun copy(context: Context, entries: List<LocalMedia>, folder: String): Result = withContext(Dispatchers.IO) {
        val copied = mutableListOf<Copied>()
        val failed = mutableListOf<String>()
        for (item in entries) {
            var target: Uri? = null
            try {
                val outputFolder = cleanFolder(folder, item.isVideo)
                val collection = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val stamp = System.currentTimeMillis()
                val originalName = item.name.substringBeforeLast('.', item.name)
                val ext = item.name.substringAfterLast('.', if (item.isVideo) "mp4" else "jpg")
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "${originalName}_$stamp.$ext")
                    put(MediaStore.MediaColumns.MIME_TYPE,
                        item.mime.ifBlank { if (item.isVideo) "video/mp4" else "image/jpeg" })
                    put(MediaStore.MediaColumns.RELATIVE_PATH, outputFolder)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                target = context.contentResolver.insert(collection, values)
                    ?: error("無法建立目的檔案")
                val outputUri = target
                context.contentResolver.openInputStream(item.uri).use { input ->
                    requireNotNull(input) { "來源檔無法開啟" }
                    context.contentResolver.openOutputStream(outputUri, "w").use { output ->
                        requireNotNull(output) { "目的檔無法開啟" }
                        input.copyTo(output, 256 * 1024)
                        output.flush()
                    }
                }
                context.contentResolver.update(outputUri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
                copied += Copied(item, outputUri)
            } catch (e: Exception) {
                target?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                failed += "${item.name}：${e.message ?: "複製失敗"}"
            }
        }
        Result(copied, failed)
    }

    fun cleanFolder(value: String, isVideo: Boolean): String {
        val path = value.replace('\\', '/').trim().trim('/')
        val safe = path.split('/').filter { it.isNotBlank() && it != "." && it != ".." }
            .map { it.replace(Regex("[^\\p{L}\\p{N} _().-]"), "_").take(80) }
            .joinToString("/")
        val fallback = if (isVideo) "Movies/MyPlayer" else "Pictures/MyPlayer"
        return (if (safe.isBlank()) fallback else safe).trimEnd('/') + "/"
    }

    /** Android MediaExtractor/MediaMuxer remux: repairs some non-seekable MP4 indexes without transcoding. */
    suspend fun repairMp4(context: Context, item: LocalMedia): Uri = withContext(Dispatchers.IO) {
        require(item.isVideo) { "僅支援影片" }
        val resolver = context.contentResolver
        val target = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,
                item.name.substringBeforeLast('.') + "_seekable_" + System.currentTimeMillis() + ".mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/MyPlayer/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("無法建立修復副本")
        var completed = false
        try {
            resolver.openFileDescriptor(item.uri, "r").use { input ->
                requireNotNull(input) { "無法讀取來源影片" }
                resolver.openFileDescriptor(target, "rw").use { output ->
                    requireNotNull(output) { "無法建立輸出影片" }
                    val extractor = MediaExtractor()
                    var muxer: MediaMuxer? = null
                    try {
                        extractor.setDataSource(input.fileDescriptor)
                        muxer = MediaMuxer(output.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        val mapping = mutableMapOf<Int, Int>()
                        for (track in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(track)
                            val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                                mapping[track] = muxer.addTrack(format)
                                extractor.selectTrack(track)
                            }
                        }
                        require(mapping.isNotEmpty()) { "沒有可重新封裝的音訊或影片軌" }
                        muxer.start()
                        val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
                        val info = MediaCodec.BufferInfo()
                        while (true) {
                            val sourceTrack = extractor.sampleTrackIndex
                            if (sourceTrack < 0) break
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) break
                            val outputTrack = mapping[sourceTrack]
                            if (outputTrack != null) {
                                info.set(0, size, extractor.sampleTime.coerceAtLeast(0),
                                    extractor.sampleFlags)
                                muxer.writeSampleData(outputTrack, buffer, info)
                            }
                            extractor.advance()
                        }
                        muxer.stop()
                        completed = true
                    } finally {
                        runCatching { muxer?.release() }
                        runCatching { extractor.release() }
                    }
                }
            }
            resolver.update(target, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            target
        } finally {
            if (!completed) runCatching { resolver.delete(target, null, null) }
        }
    }
}
