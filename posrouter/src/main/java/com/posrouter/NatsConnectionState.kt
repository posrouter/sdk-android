package com.posrouter

enum class NatsConnectionState {
    OFFLINE,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

internal fun com.posrouter.core.lensing.LensingState.toNatsConnectionState(): NatsConnectionState =
    when (this) {
        com.posrouter.core.lensing.LensingState.IDLE -> NatsConnectionState.OFFLINE
        com.posrouter.core.lensing.LensingState.DISCOVERING -> NatsConnectionState.DISCOVERING
        com.posrouter.core.lensing.LensingState.CONNECTING -> NatsConnectionState.CONNECTING
        com.posrouter.core.lensing.LensingState.CONNECTED -> NatsConnectionState.CONNECTED
        com.posrouter.core.lensing.LensingState.RECONNECTING -> NatsConnectionState.RECONNECTING
        com.posrouter.core.lensing.LensingState.FAILED -> NatsConnectionState.FAILED
    }
