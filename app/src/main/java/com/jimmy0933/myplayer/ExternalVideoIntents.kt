package com.jimmy0933.myplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** One externally supplied video, with a token so reopening the same URI works. */
internal data class ExternalVideoRequest(
    val uri: Uri,
    val mimeType: String,
    val requestId: Int
)

internal object ExternalVideoIntents {
    fun accepts(action: String?, mimeType: String?, scheme: String?): Boolean =
        action == Intent.ACTION_VIEW &&
            mimeType?.startsWith("video/", ignoreCase = true) == true &&
            (scheme.equals("content", ignoreCase = true) ||
                scheme.equals("file", ignoreCase = true))

    fun parse(intent: Intent?, requestId: Int): ExternalVideoRequest? {
        val uri = intent?.data ?: return null
        if (!accepts(intent.action, intent.type, uri.scheme)) return null
        return ExternalVideoRequest(uri, intent.type ?: "video/*", requestId)
    }

    fun asMedia(context: Context, request: ExternalVideoRequest): LocalMedia {
        // Some document providers use an opaque ID as the last path segment.
        // Read the display name through the temporary URI permission from VIEW.
        val displayName = runCatching {
            context.contentResolver.query(
                request.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val fallbackName = request.uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() && it.length <= 100 }
            ?: "外部影片"
        return LocalMedia(
            uri = request.uri,
            id = 0L,
            name = displayName?.takeIf(String::isNotBlank) ?: fallbackName,
            mime = request.mimeType,
            size = 0L,
            dateAdded = 0L,
            durationMs = 0L,
            relativePath = "",
            isVideo = true
        )
    }
}
