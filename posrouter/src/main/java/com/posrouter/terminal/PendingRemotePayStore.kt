package com.posrouter.terminal

import com.posrouter.POSRouterTerminalListener
import com.posrouter.RemotePaymentRequest

/**
 * Holds a remote pay that arrived before any terminal UI was bound, so it is delivered once the
 * listener appears instead of being dropped. Keeps the whole [RemotePaymentRequest] — including the
 * initiator's metadata — because a queued order must arrive with the same instructions a live one
 * would have carried.
 */
internal object PendingRemotePayStore {

    @Volatile
    private var pending: RemotePaymentRequest? = null

    fun store(request: RemotePaymentRequest) {
        pending = request
    }

    fun peek(): RemotePaymentRequest? = pending

    fun clear() {
        pending = null
    }

    fun drain(listener: POSRouterTerminalListener) {
        val request = pending ?: return
        pending = null
        listener.onRemotePaymentReceived(request)
    }
}
