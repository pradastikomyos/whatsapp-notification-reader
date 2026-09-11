package com.ridenotify.app.wa_reader

import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationSmokeTest {
    @Test
    fun packageIdentityRemainsStable() {
        assertEquals("com.ridenotify.app.wa_reader", MainActivity::class.java.packageName)
    }

    @Test
    fun listenerComponentIdentityRemainsStable() {
        assertEquals(
            "com.ridenotify.app.wa_reader.MyNotificationListener",
            MyNotificationListener::class.java.name,
        )
    }
}
