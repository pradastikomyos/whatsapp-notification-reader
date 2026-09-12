package com.ridenotify.app.wa_reader

import android.app.Application
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WaReaderApplicationDatabaseRetirementTest {
    @Test
    fun `observed conversations database retirement is idempotent`() {
        val application = RuntimeEnvironment.getApplication() as Application
        val databaseFile = application.getDatabasePath("observed_conversations.db")
        databaseFile.parentFile?.mkdirs()
        assertTrue(databaseFile.createNewFile())

        assertTrue(retireObservedConversationsDatabase(application))
        assertFalse(databaseFile.exists())
        assertFalse(retireObservedConversationsDatabase(application))
    }
}
