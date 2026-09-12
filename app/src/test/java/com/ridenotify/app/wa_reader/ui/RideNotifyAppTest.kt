package com.ridenotify.app.wa_reader.ui

import com.ridenotify.app.wa_reader.R
import com.ridenotify.app.wa_reader.data.settings.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class RideNotifyAppTest {
    @Test
    fun `shell exposes exactly the four direct-message app destinations`() {
        assertEquals(
            listOf(
                R.string.navigation_status,
                R.string.navigation_home,
                R.string.navigation_riding,
                R.string.navigation_settings,
            ),
            AppDestination.entries.map { it.titleRes },
        )
    }

    @Test
    fun `theme button switches from the effective theme`() {
        assertEquals(ThemeMode.DARK, nextThemeMode(currentlyDark = false))
        assertEquals(ThemeMode.LIGHT, nextThemeMode(currentlyDark = true))
    }
}
