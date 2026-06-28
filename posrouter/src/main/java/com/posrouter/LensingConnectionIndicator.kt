package com.posrouter

/**
 * Canonical ARGB indicator colors for [LensingConnectionState].
 * Use these (via [LensingConnectionState.indicatorColorArgb] or [POSRouter.lensingIndicatorColor])
 * so status dots match across demo, kiosk, and partner apps.
 */
object LensingConnectionIndicator {

    /** Connected — Lensing / NATS session ready. */
    const val COLOR_CONNECTED: Int = 0xFF22C55E.toInt()

    /** Discovering, connecting, or reconnecting. */
    const val COLOR_CONNECTING: Int = 0xFFF59E0B.toInt()

    /** Gateway discovery or session failed. */
    const val COLOR_FAILED: Int = 0xFFEF4444.toInt()

    /** Not initialized or idle. */
    const val COLOR_OFFLINE: Int = 0xFF94A3B8.toInt()

    /**
     * Half-cycle (fade in or fade out) for connecting-state pulse animation, in ms.
     * Full cycle ≈ 10 s → ~6 breaths/min, aligned with calm "resonant" breathing (~5–7/min).
     */
    const val PULSE_HALF_CYCLE_MS: Long = 5_000L

    /** Alpha at the dimmest point of the pulse (1f = fully opaque). */
    const val PULSE_ALPHA_MIN: Float = 0.45f

    const val PULSE_ALPHA_MAX: Float = 1f

    @JvmStatic
    fun colorArgb(state: LensingConnectionState): Int = when (state) {
        LensingConnectionState.CONNECTED -> COLOR_CONNECTED
        LensingConnectionState.DISCOVERING,
        LensingConnectionState.CONNECTING,
        LensingConnectionState.RECONNECTING -> COLOR_CONNECTING
        LensingConnectionState.FAILED -> COLOR_FAILED
        LensingConnectionState.OFFLINE -> COLOR_OFFLINE
    }
}

/** @return ARGB color for a Lensing status indicator dot. */
fun LensingConnectionState.indicatorColorArgb(): Int = LensingConnectionIndicator.colorArgb(this)
