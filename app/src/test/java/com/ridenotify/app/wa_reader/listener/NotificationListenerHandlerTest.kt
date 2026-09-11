package com.ridenotify.app.wa_reader.listener

import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationListenerHandlerTest {
    @Test
    fun `only exact WhatsApp packages are extracted and submitted`() {
        val submitted = mutableListOf<NotificationSnapshot>()
        val handler = NotificationListenerHandler { snapshot -> submitted += snapshot; true }
        var extractionCount = 0

        listOf("com.example", "com.whatsapp.clone", "COM.WHATSAPP").forEach { packageName ->
            assertFalse(handler.notificationPosted(packageName) {
                extractionCount++
                snapshot(packageName)
            })
        }
        listOf("com.whatsapp", "com.whatsapp.w4b").forEach { packageName ->
            assertTrue(handler.notificationPosted(packageName) {
                extractionCount++
                snapshot(packageName)
            })
        }

        assertEquals(2, extractionCount)
        assertEquals(listOf("com.whatsapp", "com.whatsapp.w4b"), submitted.map { it.packageName })
    }

    @Test
    fun `submission failure is reported without retrying callback work`() {
        var submissionCount = 0
        val handler = NotificationListenerHandler { submissionCount++; false }

        assertFalse(handler.notificationPosted("com.whatsapp") { snapshot("com.whatsapp") })
        assertEquals(1, submissionCount)
    }

    @Test
    fun `malformed notification extraction fails closed`() {
        var submissionCount = 0
        val handler = NotificationListenerHandler { submissionCount++; true }

        assertFalse(handler.notificationPosted("com.whatsapp") { error("malformed extras") })
        assertEquals(0, submissionCount)
    }

    private fun snapshot(packageName: String) = NotificationSnapshot(
        packageName = packageName,
        notificationKey = "key",
        notificationId = 1,
        postTimeMillis = 1,
        groupKey = null,
        title = "Synthetic",
        text = "Synthetic body",
        bigText = null,
        textLines = emptyList(),
        subText = null,
        summaryText = null,
        category = "msg",
        isGroupSummary = false,
        conversationTitle = null,
        shortcutId = null,
        messagingStyle = null,
    )
}
