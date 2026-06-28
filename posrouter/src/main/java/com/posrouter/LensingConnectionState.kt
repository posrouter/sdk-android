package com.posrouter

enum class LensingConnectionState {
    OFFLINE,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

internal fun com.posrouter.core.lensing.LensingState.toLensingConnectionState(): LensingConnectionState =
    when (this) {
        com.posrouter.core.lensing.LensingState.IDLE -> LensingConnectionState.OFFLINE
        com.posrouter.core.lensing.LensingState.DISCOVERING -> LensingConnectionState.DISCOVERING
        com.posrouter.core.lensing.LensingState.CONNECTING -> LensingConnectionState.CONNECTING
        com.posrouter.core.lensing.LensingState.CONNECTED -> LensingConnectionState.CONNECTED
        com.posrouter.core.lensing.LensingState.RECONNECTING -> LensingConnectionState.RECONNECTING
        com.posrouter.core.lensing.LensingState.FAILED -> LensingConnectionState.FAILED
    }
