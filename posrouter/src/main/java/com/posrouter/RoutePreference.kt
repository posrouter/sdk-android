package com.posrouter

/**
 * Runtime routing preference for local acquirer launch vs NATS (Lensing).
 * Values are strings so apps, config, and future wire formats can pass them directly.
 */
object RoutePreference {
    const val AUTO = "auto"
    const val LOCAL_FIRST = "local_first"
    const val REMOTE_FIRST = "remote_first"
    const val LOCAL_ONLY = "local_only"
    const val REMOTE_ONLY = "remote_only"
    /**
     * Same-device POSRouter Kiosk method picker via `posrouter-kiosk://charge`
     * (not local acquirer card/QR, not NATS).
     */
    const val LOCAL_POSROUTER_KIOSK = "local_posrouter_kiosk"

    private val KNOWN = setOf(
        AUTO,
        LOCAL_FIRST,
        REMOTE_FIRST,
        LOCAL_ONLY,
        REMOTE_ONLY,
        LOCAL_POSROUTER_KIOSK
    )

    /** Blank or unknown values resolve to [AUTO]. */
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return AUTO
        val normalized = value.trim().lowercase().replace('-', '_')
        return if (normalized in KNOWN) normalized else AUTO
    }
}
