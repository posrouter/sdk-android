package com.posrouter.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts [LensingTerminalService] after device boot when terminal mode was enabled. */
class TerminalBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!TerminalModeStore.isEnabled(context)) return
        Log.i(TAG, "Boot completed — starting terminal service")
        LensingTerminalService.start(context)
    }

    companion object {
        private const val TAG = "POSRouter.TerminalBoot"
    }
}
