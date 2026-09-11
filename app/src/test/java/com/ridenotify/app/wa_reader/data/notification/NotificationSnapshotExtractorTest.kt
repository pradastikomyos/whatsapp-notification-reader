package com.ridenotify.app.wa_reader.data.notification

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationSnapshotExtractorTest {
    private val context: Context = RuntimeEnvironment.getApplication().applicationContext
    private val extractor = NotificationSnapshotExtractor()

    @Test
    fun extract_copiesNotificationMetadataAndExtras() {
        val notification = Notification.Builder(context, "messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Conversation")
            .setContentText("Message")
            .setSubText("Subtext")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setGroup("message-bundle")
            .setGroupSummary(true)
            .setShortcutId("conversation-shortcut")
            .setStyle(
                Notification.BigTextStyle()
                    .bigText("Expanded message")
                    .setSummaryText("Summary"),
            )
            .build()
            .also {
                it.extras.putCharSequenceArray(
                    Notification.EXTRA_TEXT_LINES,
                    arrayOf("Line one", "Line two"),
                )
                it.extras.putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "Structured title")
            }
        val statusBarNotification = statusBarNotification(notification)

        val snapshot = extractor.extract(statusBarNotification)

        assertEquals(PACKAGE_NAME, snapshot.packageName)
        assertEquals(statusBarNotification.key, snapshot.notificationKey)
        assertEquals(NOTIFICATION_ID, snapshot.notificationId)
        assertEquals(POST_TIME, snapshot.postTimeMillis)
        assertEquals("message-bundle", snapshot.groupKey)
        assertEquals("Conversation", snapshot.title)
        assertEquals("Message", snapshot.text)
        assertEquals("Expanded message", snapshot.bigText)
        assertEquals(listOf("Line one", "Line two"), snapshot.textLines)
        assertEquals("Subtext", snapshot.subText)
        assertEquals("Summary", snapshot.summaryText)
        assertEquals(Notification.CATEGORY_MESSAGE, snapshot.category)
        assertTrue(snapshot.isGroupSummary)
        assertEquals("Structured title", snapshot.conversationTitle)
        assertEquals("conversation-shortcut", snapshot.shortcutId)
        assertNull(snapshot.messagingStyle)
    }

    @Test
    fun extract_copiesMessagingStyleConversationAndMessages() {
        val user = Person.Builder().setName("Device owner").setKey("self-key").build()
        val sender = Person.Builder()
            .setName("Synthetic sender")
            .setKey("sender-key")
            .setBot(true)
            .build()
        val style = Notification.MessagingStyle(user)
            .setConversationTitle("Synthetic group")
            .setGroupConversation(true)
            .addMessage("First", 100L, sender)
            .addMessage(Notification.MessagingStyle.Message("Second", 200L, sender))
        val notification = Notification.Builder(context, "messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(style)
            .build()

        val snapshot = extractor.extract(statusBarNotification(notification))
        val messagingStyle = requireNotNull(snapshot.messagingStyle)

        assertEquals("Device owner", messagingStyle.userDisplayName)
        assertTrue(messagingStyle.isGroupConversation)
        assertEquals("Synthetic group", messagingStyle.conversationTitle)
        assertEquals(listOf("First", "Second"), messagingStyle.messages.map { it.text })
        assertEquals(listOf(100L, 200L), messagingStyle.messages.map { it.timestampMillis })
        assertEquals("sender-key", messagingStyle.messages.first().sender?.key)
        assertEquals("Synthetic sender", messagingStyle.messages.first().sender?.name)
        assertTrue(messagingStyle.messages.first().sender?.isBot == true)
    }

    @Test
    fun extract_keepsOsGroupSeparateFromConversationGroupMetadata() {
        val user = Person.Builder().setName("Device owner").build()
        val notification = Notification.Builder(context, "messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setGroup("os-bundle")
            .setStyle(Notification.MessagingStyle(user).setGroupConversation(false))
            .build()

        val snapshot = extractor.extract(statusBarNotification(notification))

        assertEquals("os-bundle", snapshot.groupKey)
        assertFalse(requireNotNull(snapshot.messagingStyle).isGroupConversation)
    }

    @Test
    fun extract_handlesMissingOptionalFields() {
        val notification = Notification.Builder(context, "messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .build()

        val snapshot = extractor.extract(statusBarNotification(notification))

        assertNull(snapshot.groupKey)
        assertNull(snapshot.title)
        assertNull(snapshot.text)
        assertNull(snapshot.bigText)
        assertTrue(snapshot.textLines.isEmpty())
        assertNull(snapshot.subText)
        assertNull(snapshot.summaryText)
        assertNull(snapshot.conversationTitle)
        assertNull(snapshot.shortcutId)
        assertNull(snapshot.messagingStyle)
    }

    @Suppress("DEPRECATION") // The public SDK exposes only this constructor through API 36.
    private fun statusBarNotification(notification: Notification) = StatusBarNotification(
        PACKAGE_NAME,
        PACKAGE_NAME,
        NOTIFICATION_ID,
        "notification-tag",
            Process.myUid(),
            Process.myPid(),
            0,
            notification,
            Process.myUserHandle(),
            POST_TIME,
        )

    private companion object {
        const val PACKAGE_NAME = "com.whatsapp"
        const val NOTIFICATION_ID = 42
        const val POST_TIME = 1_700_000_000_000L
    }
}
