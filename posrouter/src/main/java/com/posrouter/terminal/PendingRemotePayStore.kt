package com.posrouter.terminal

import com.posrouter.POSRouterTerminalListener

internal object PendingRemotePayStore {

    data class Snapshot(
        val orderId: String,
        val amountCents: Long,
        val currency: String,
        val remark: String?,
        val method: String?
    )

    @Volatile
    private var pending: Snapshot? = null

    fun store(
        orderId: String,
        amountCents: Long,
        currency: String,
        remark: String?,
        method: String?
    ) {
        pending = Snapshot(orderId, amountCents, currency, remark, method)
    }

    fun peek(): Snapshot? = pending

    fun clear() {
        pending = null
    }

    fun drain(listener: POSRouterTerminalListener) {
        val snapshot = pending ?: return
        pending = null
        listener.onRemotePaymentReceived(
            orderId = snapshot.orderId,
            amountCents = snapshot.amountCents,
            currency = snapshot.currency,
            remark = snapshot.remark,
            method = snapshot.method
        )
    }
}
