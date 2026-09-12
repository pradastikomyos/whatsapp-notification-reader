package com.ridenotify.app.wa_reader.listener

import android.content.ComponentName
import com.ridenotify.app.wa_reader.MyNotificationListener
import com.ridenotify.app.wa_reader.WaReaderApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = WaReaderApplication::class)
class MyNotificationListenerLifecycleTest {

    @Test
    fun `listener callbacks update application scoped connection state`() {
        val listener = Robolectric.buildService(MyNotificationListener::class.java)
            .create()
            .get()
        val application = RuntimeEnvironment.getApplication() as WaReaderApplication
        val rebindController = RecordingRebindController()
        val originalController = application.appContainer.listenerRebindController
        try {
            application.appContainer.replaceListenerRebindControllerForTest(rebindController)

            listener.onListenerConnected()
            assertEquals(1, rebindController.connectedCalls)
            assertEquals(
                ListenerConnectionState.CONNECTED,
                application.appContainer.listenerConnectionTracker.state.value,
            )

            listener.onDestroy()
            assertEquals(
                ListenerConnectionState.DISCONNECTED,
                application.appContainer.listenerConnectionTracker.state.value,
            )
        } finally {
            application.appContainer.replaceListenerRebindControllerForTest(originalController)
        }
    }

    private class RecordingRebindController : ListenerRebindController {
        var connectedCalls = 0

        override fun onConnected() {
            connectedCalls++
        }

        override fun onDisconnected(componentName: ComponentName): Boolean = false
    }
}
