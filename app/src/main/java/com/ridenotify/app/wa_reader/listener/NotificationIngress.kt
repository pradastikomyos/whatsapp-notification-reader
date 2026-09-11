package com.ridenotify.app.wa_reader.listener

import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

fun interface NotificationIngress {
    fun trySubmit(snapshot: NotificationSnapshot): Boolean
}

class SerializedNotificationIngress(
    capacity: Int = DEFAULT_CAPACITY,
) : NotificationIngress {
    private val channel = Channel<NotificationSnapshot>(
        capacity = capacity.also { require(it > 0) { "capacity must be positive" } },
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val snapshots: Flow<NotificationSnapshot> = channel.receiveAsFlow()

    override fun trySubmit(snapshot: NotificationSnapshot): Boolean = channel.trySend(snapshot).isSuccess

    companion object {
        const val DEFAULT_CAPACITY = 64
    }
}
