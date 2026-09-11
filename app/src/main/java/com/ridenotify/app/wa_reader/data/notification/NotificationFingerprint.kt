package com.ridenotify.app.wa_reader.data.notification

import com.ridenotify.app.wa_reader.model.ParsedMessage
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** An opaque, content-free identifier used only by the in-memory deduplication cache. */
@JvmInline
value class NotificationFingerprint private constructor(private val digest: String) {
    companion object {
        fun from(packageName: String, message: ParsedMessage): NotificationFingerprint {
            require(packageName.isNotBlank()) { "packageName must not be blank" }

            val fields = listOf(
                normalize(packageName).lowercase(Locale.ROOT),
                normalize(message.conversationId.value),
                normalize(message.senderDisplayName.orEmpty()),
                normalize(message.body),
                message.postedAtMillis.toString(),
            )
            val input = buildString {
                fields.forEach { field ->
                    // Length-prefixing prevents different field boundaries from producing the same input.
                    append(field.length).append(':').append(field)
                }
            }
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(StandardCharsets.UTF_8))
            return NotificationFingerprint(bytes.joinToString(separator = "") { "%02x".format(it) })
        }

        private fun normalize(value: String): String =
            Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
                .replace(WHITESPACE, " ")

        private val WHITESPACE = Regex("\\s+")
    }
}
