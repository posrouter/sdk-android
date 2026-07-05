package com.posrouter.terminal

import java.lang.ref.WeakReference

/** Lets [LensingTerminalService] refresh its foreground notification when Lensing state changes. */
internal object TerminalNotificationRefresher {
    private var serviceRef: WeakReference<LensingTerminalService>? = null

    fun bind(service: LensingTerminalService) {
        serviceRef = WeakReference(service)
    }

    fun unbind(service: LensingTerminalService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
        }
    }

    fun refresh() {
        serviceRef?.get()?.refreshForegroundNotification()
    }
}
