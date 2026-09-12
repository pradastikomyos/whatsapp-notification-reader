package com.ridenotify.app.wa_reader.listener

import kotlin.test.Test
import kotlin.test.assertEquals

class ListenerConnectionTrackerTest {
    @Test
    fun `connection state starts disconnected and follows callbacks`() {
        val tracker = ListenerConnectionTracker()
        assertEquals(ListenerConnectionState.DISCONNECTED, tracker.state.value)

        tracker.connected()
        assertEquals(ListenerConnectionState.CONNECTED, tracker.state.value)

        tracker.disconnected()
        assertEquals(ListenerConnectionState.DISCONNECTED, tracker.state.value)

        tracker.rebindRequested()
        assertEquals(ListenerConnectionState.REBIND_REQUESTED, tracker.state.value)

        tracker.connected()
        assertEquals(ListenerConnectionState.CONNECTED, tracker.state.value)

        tracker.disconnected()
        assertEquals(ListenerConnectionState.DISCONNECTED, tracker.state.value)
    }
}
