package com.ridenotify.app.wa_reader.listener

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ListenerConnectionState {
    DISCONNECTED,
    REBIND_REQUESTED,
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

    fun rebindRequested() {
        mutableState.value = ListenerConnectionState.REBIND_REQUESTED
    }
}
