package com.quvntvn.carlocator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConnectionState {
    private val _connectedCarName = MutableStateFlow<String?>(null)
    val connectedCarName = _connectedCarName.asStateFlow()

    fun onConnected(name: String) {
        _connectedCarName.value = name
    }

    fun onDisconnected() {
        _connectedCarName.value = null
    }
}
