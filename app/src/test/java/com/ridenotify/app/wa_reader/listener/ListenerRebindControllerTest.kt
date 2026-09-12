package com.ridenotify.app.wa_reader.listener

import android.content.ComponentName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ListenerRebindControllerTest {

    private val testComponent = ComponentName("com.ridenotify.app.wa_reader", "com.ridenotify.app.wa_reader.MyNotificationListener")

    @Test
    fun `requests rebind when package is enabled`() {
        var rebindCalledWith: ComponentName? = null
        val controller = DefaultListenerRebindController(
            enabledCheck = { true },
            clockMillis = { 1_000L },
            requestRebind = { component -> rebindCalledWith = component },
        )

        val result = controller.onDisconnected(testComponent)

        assertTrue(result)
        assertEquals(testComponent, rebindCalledWith)
    }

    @Test
    fun `does not request rebind when package is not enabled`() {
        var rebindCalled = false
        val controller = DefaultListenerRebindController(
            enabledCheck = { false },
            clockMillis = { 1_000L },
            requestRebind = { rebindCalled = true },
        )

        val result = controller.onDisconnected(testComponent)

        assertFalse(result)
        assertFalse(rebindCalled)
    }

    @Test
    fun `respects dynamic change in enabled state`() {
        var isEnabled = true
        var now = 1_000L
        var rebindCallCount = 0
        val controller = DefaultListenerRebindController(
            enabledCheck = { isEnabled },
            clockMillis = { now },
            requestRebind = { rebindCallCount++ },
        )

        assertTrue(controller.onDisconnected(testComponent))
        assertEquals(1, rebindCallCount)

        isEnabled = false
        now += DefaultListenerRebindController.DEFAULT_MINIMUM_INTERVAL_MILLIS
        assertFalse(controller.onDisconnected(testComponent))
        assertEquals(1, rebindCallCount)
    }

    @Test
    fun `rate limits repeated disconnect callbacks`() {
        var now = 1_000L
        var rebindCallCount = 0
        val controller = DefaultListenerRebindController(
            enabledCheck = { true },
            clockMillis = { now },
            minimumIntervalMillis = 30_000L,
            requestRebind = { rebindCallCount++ },
        )

        assertTrue(controller.onDisconnected(testComponent))
        assertFalse(controller.onDisconnected(testComponent))
        now += 29_999L
        assertFalse(controller.onDisconnected(testComponent))
        now += 1L
        assertTrue(controller.onDisconnected(testComponent))

        assertEquals(2, rebindCallCount)
    }

    @Test
    fun `connection success resets throttle for a later disconnect`() {
        var now = 1_000L
        var rebindCallCount = 0
        val controller = DefaultListenerRebindController(
            enabledCheck = { true },
            clockMillis = { now },
            requestRebind = { rebindCallCount++ },
        )

        assertTrue(controller.onDisconnected(testComponent))
        now += 1L
        controller.onConnected()
        assertTrue(controller.onDisconnected(testComponent))
        assertEquals(2, rebindCallCount)
    }

    @Test
    fun `rebind failure is rate limited before a later retry`() {
        var now = 1_000L
        var attemptCount = 0
        val controller = DefaultListenerRebindController(
            enabledCheck = { true },
            clockMillis = { now },
            requestRebind = {
                attemptCount++
                if (attemptCount == 1) error("system rejected rebind")
            },
        )

        assertFalse(controller.onDisconnected(testComponent))
        assertFalse(controller.onDisconnected(testComponent))
        now += DefaultListenerRebindController.DEFAULT_MINIMUM_INTERVAL_MILLIS
        assertTrue(controller.onDisconnected(testComponent))
        assertEquals(2, attemptCount)
    }

    @Test
    fun `enabled listener lookup failure fails closed`() {
        val controller = DefaultListenerRebindController(
            enabledCheck = { error("settings provider unavailable") },
            clockMillis = { 1_000L },
            requestRebind = { error("must not request rebind") },
        )

        assertFalse(controller.onDisconnected(testComponent))
    }
}
