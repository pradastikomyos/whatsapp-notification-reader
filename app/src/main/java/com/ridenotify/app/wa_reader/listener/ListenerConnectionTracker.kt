package com.ridenotify.app.wa_reader.listener

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ListenerConnectionState {
    DISCONNECTED,
    CONNECTED,
}

class ListenerConnectionTracker {
    private val mutableState = MutableStateFlow(ListenerConnectionState.DISCONNECTED)
    val state: StateFlow<ListenerConnectionState> = mutableState.asStateFlow()

    fun connected() {
        mutableState.value = ListenerConnectionState.CONNECTED
    }

    fun disconnected() {
        mutableState.value = ListenerConnectionState.DISCONNECTED
    }
}
