package com.jimmy0933.myplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalVideoIntentsTest {
    @Test fun supportsDownloadedVideosFromContentProviders() {
        assertTrue(ExternalVideoIntents.accepts(
            "android.intent.action.VIEW", "video/mp4", "content"))
        assertTrue(ExternalVideoIntents.accepts(
            "android.intent.action.VIEW", "video/mp2t", "content"))
        assertTrue(ExternalVideoIntents.accepts(
            "android.intent.action.VIEW", "video/*", "file"))
    }

    @Test fun rejectsNonVideoAndUnsupportedSchemes() {
        assertFalse(ExternalVideoIntents.accepts(
            "android.intent.action.VIEW", "image/jpeg", "content"))
        assertFalse(ExternalVideoIntents.accepts(
            "android.intent.action.SEND", "video/mp4", "content"))
        assertFalse(ExternalVideoIntents.accepts(
            "android.intent.action.VIEW", "video/mp4", "https"))
        assertFalse(ExternalVideoIntents.accepts(
            "android.intent.action.VIEW", null, "content"))
    }
}
