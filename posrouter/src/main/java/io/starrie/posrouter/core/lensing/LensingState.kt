package io.starrie.posrouter.core.lensing

enum class LensingState {
    IDLE,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}
