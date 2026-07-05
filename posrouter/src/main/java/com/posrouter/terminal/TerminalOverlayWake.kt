package com.posrouter.terminal

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Apps holding [Settings.canDrawOverlays] are exempt from background-activity-start blocks (API 29+).
 * B-side terminal integrators should guide staff to enable "Display over other apps" once during setup.
 */
internal object TerminalOverlayWake {
    private const val TAG = "POSRouter.TerminalOverlay"

    fun isPrivileged(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun launch(context: Context, intent: Intent): Boolean {
        if (!isPrivileged(context)) {
            Log.i(TAG, "Overlay permission not granted — privileged wake unavailable")
            return false
        }
        if (LensingTerminalService.launchTerminalActivity(intent)) {
            Log.i(TAG, "Overlay-privileged launch via FGS")
            return true
        }
        return try {
            context.startActivity(intent)
            Log.i(TAG, "Overlay-privileged launch via app context")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Overlay-privileged launch failed", e)
            false
        }
    }
}
