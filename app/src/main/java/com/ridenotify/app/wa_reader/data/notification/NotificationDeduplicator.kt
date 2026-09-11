package com.ridenotify.app.wa_reader.data.notification

import com.ridenotify.app.wa_reader.model.ParsedMessage

/**
 * Pure, bounded, in-memory duplicate detection.
 *
 * Cache age is measured by [clock], not by notification timestamps. Source timestamps remain
 * part of the fingerprint so rapid, otherwise-identical messages are not collapsed.
 */
class NotificationDeduplicator(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val observedAt = LinkedHashMap<NotificationFingerprint, Long>()

    init {
        require(capacity > 0) { "capacity must be positive" }
        require(windowMillis > 0) { "windowMillis must be positive" }
    }

    /** Returns only messages not observed during the active window, preserving input order. */
    @Synchronized
    fun filterNew(packageName: String, messages: List<ParsedMessage>): List<ParsedMessage> {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        val now = clock()
        removeExpired(now)

        return messages.filter { message ->
            val fingerprint = NotificationFingerprint.from(packageName, message)
            val previous = observedAt[fingerprint]
            if (previous != null && elapsed(now, previous) < windowMillis) {
                false
            } else {
                observedAt[fingerprint] = now
                trimToCapacity()
                true
            }
        }
    }

    @Synchronized
    fun clear() = observedAt.clear()

    private fun removeExpired(now: Long) {
        val iterator = observedAt.entries.iterator()
        while (iterator.hasNext()) {
            if (elapsed(now, iterator.next().value) >= windowMillis) iterator.remove()
        }
    }

    private fun trimToCapacity() {
        while (observedAt.size > capacity) {
            val iterator = observedAt.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    private fun elapsed(now: Long, then: Long): Long =
        if (now >= then) now - then else Long.MAX_VALUE

    companion object {
        const val DEFAULT_CAPACITY = 256
        const val DEFAULT_WINDOW_MILLIS = 180_000L
    }
}
