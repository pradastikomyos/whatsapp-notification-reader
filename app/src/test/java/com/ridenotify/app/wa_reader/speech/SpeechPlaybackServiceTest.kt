package com.ridenotify.app.wa_reader.speech

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import com.ridenotify.app.wa_reader.WaReaderApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = WaReaderApplication::class, sdk = [34])
class SpeechPlaybackServiceTest {

    @Test
    fun `API 26 through 34 does not require a foreground playback service`() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as WaReaderApplication
        val gate = AndroidForegroundPlaybackGate(context, sdkInt = 34)

        assertEquals(ForegroundPlaybackPromotion.NotRequired, gate.ensurePromoted())
        gate.stop()
    }

    @Test
    fun `start command promotes service before exposing success through its binder`() = runBlocking {
        val service = Robolectric.buildService(SpeechPlaybackService::class.java)
            .create()
            .get()

        service.onStartCommand(SpeechPlaybackService.startIntent(service), 0, 1)
        val binder = service.onBind(null) as SpeechPlaybackService.LocalBinder

        assertEquals(SpeechPlaybackService.PromotionState.PROMOTED, binder.awaitPromotion())
        val manager = service.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel("speech_playback"))
        Unit
    }

    @Test
    fun `manifest declares private media playback foreground service and permissions`() {
        val context = RuntimeEnvironment.getApplication() as WaReaderApplication
        val serviceInfo = context.packageManager.getServiceInfo(
            android.content.ComponentName(context, SpeechPlaybackService::class.java),
            0,
        )

        assertEquals(false, serviceInfo.exported)
        assertEquals(
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            serviceInfo.foregroundServiceType,
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(
                "android.permission.FOREGROUND_SERVICE",
                context.packageName,
            ),
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(
                "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                context.packageName,
            ),
        )
    }
}
