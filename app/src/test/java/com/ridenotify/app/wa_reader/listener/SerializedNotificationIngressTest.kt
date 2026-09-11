package com.ridenotify.app.wa_reader.listener

import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SerializedNotificationIngressTest {
    @Test
    fun `handoff preserves order and drops oldest when bounded capacity is full`() = runTest {
        val ingress = SerializedNotificationIngress(capacity = 2)

        assertTrue(ingress.trySubmit(snapshot(1)))
        assertTrue(ingress.trySubmit(snapshot(2)))
        assertTrue(ingress.trySubmit(snapshot(3)))

        assertEquals(listOf(2, 3), ingress.snapshots.take(2).toList().map { it.notificationId })
    }

    @Test
    fun `capacity must be positive`() {
        assertFailsWith<IllegalArgumentException> { SerializedNotificationIngress(0) }
    }

    private fun snapshot(id: Int) = NotificationSnapshot(
        packageName = "com.whatsapp",
        notificationKey = "key-$id",
        notificationId = id,
        postTimeMillis = id.toLong(),
        groupKey = null,
        title = null,
        text = null,
        bigText = null,
        textLines = emptyList(),
        subText = null,
        summaryText = null,
        category = null,
        isGroupSummary = false,
        conversationTitle = null,
        shortcutId = null,
        messagingStyle = null,
    )
}
