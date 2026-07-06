package com.posrouter.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.posrouter.LensingContextHolder
import com.posrouter.core.lensing.LensingProtocolEngine

/** Starts [LensingTerminalService] after device boot when terminal mode was enabled. */
class TerminalBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in BOOT_ACTIONS) return
        if (!TerminalModeStore.isEnabled(context)) return
        Log.i(TAG, "Boot event ($action) — starting terminal service")
        LensingTerminalService.start(context)
        LensingContextHolder.config?.let { config ->
            LensingProtocolEngine.start(config, force = true)
        }
    }

    companion object {
        private const val TAG = "POSRouter.TerminalBoot"
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON"
        )
    }
}
