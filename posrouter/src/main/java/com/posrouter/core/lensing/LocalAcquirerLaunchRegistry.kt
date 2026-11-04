package com.posrouter.core.lensing

import java.util.Collections

/**
 * Per-order marker: did this terminal actually launch a local acquirer (card / QR / Skyzer) for the
 * order, or is it still sitting on the method picker with nothing launched?
 *
 * [LensingProtocolEngine.handleIncomingVoid] uses it to tell the two apart:
 *  - launched  → an acquirer may be mid-transaction, so keep the VOID_ACK_FALLBACK_MS window and wait
 *                for its real final result (so a live charge is never cancelled out from under it).
 *  - not launched → nobody downstream will ever answer, so synthesize the CANCELLED immediately
 *                instead of dead-waiting the full fallback window (the "cancel is slow on the picker"
 *                bug: a method-selection order had no acquirer, yet the void still waited 8s).
 *
 * Marked where the acquirer is actually launched: [com.posrouter.POSRouter.launchPendingLocalPay]
 * (card/QR) and the terminal app for Skyzer via [com.posrouter.POSRouter.markLocalAcquirerLaunched].
 */
internal object LocalAcquirerLaunchRegistry {
    private const val MAX_ENTRIES = 256
    private val launched: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private fun key(terminalId: String, orderId: String) = "$terminalId|$orderId"

    fun markLaunched(terminalId: String, orderId: String) {
        synchronized(launched) {
            // Orders never reuse ids, so entries only accumulate; cap instead of leaking unbounded.
            if (launched.size > MAX_ENTRIES) launched.clear()
            launched.add(key(terminalId, orderId))
        }
    }

    fun isLaunched(terminalId: String, orderId: String): Boolean =
        launched.contains(key(terminalId, orderId))

    fun clear(terminalId: String, orderId: String) {
        launched.remove(key(terminalId, orderId))
    }
}
