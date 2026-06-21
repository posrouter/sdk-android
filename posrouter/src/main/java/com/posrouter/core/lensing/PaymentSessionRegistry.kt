package com.posrouter.core.lensing

import com.posrouter.WirePaymentRequest
import java.util.concurrent.ConcurrentHashMap

/** Retains pay context until the acquirer callback arrives (amount/currency for result payload). */
internal object PaymentSessionRegistry {

    private val sessions = ConcurrentHashMap<String, WirePaymentRequest>()

    private fun key(terminalId: String, orderId: String): String = "$terminalId:$orderId"

    fun store(wire: WirePaymentRequest) {
        sessions[key(wire.terminalId, wire.orderId)] = wire
    }

    fun lookup(terminalId: String, orderId: String): WirePaymentRequest? =
        sessions[key(terminalId, orderId)]

    fun remove(terminalId: String, orderId: String) {
        sessions.remove(key(terminalId, orderId))
    }
}
