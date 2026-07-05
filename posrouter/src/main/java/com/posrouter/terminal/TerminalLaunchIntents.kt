package com.posrouter.terminal

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.posrouter.LensingContextHolder

internal object TerminalLaunchIntents {
    private const val TAG = "POSRouter.TerminalLaunch"

    fun build(
        context: Context,
        orderId: String,
        amountCents: Long,
        currency: String,
        remark: String?,
        method: String?
    ): Intent? {
        val activityClass = LensingContextHolder.config?.terminalLaunchActivityClass?.trim().orEmpty()
        if (activityClass.isEmpty()) {
            Log.w(TAG, "terminalLaunchActivityClass missing")
            return null
        }
        return payExtras(
            Intent().setClassName(context.packageName, activityClass),
            orderId,
            amountCents,
            currency,
            remark,
            method
        ).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
    }

    private fun payExtras(
        intent: Intent,
        orderId: String,
        amountCents: Long,
        currency: String,
        remark: String?,
        method: String?
    ): Intent = intent.apply {
        putExtra(TerminalUiCoordinator.EXTRA_REMOTE_PAY_ORDER_ID, orderId)
        putExtra(TerminalUiCoordinator.EXTRA_REMOTE_PAY_AMOUNT_CENTS, amountCents)
        putExtra(TerminalUiCoordinator.EXTRA_REMOTE_PAY_CURRENCY, currency)
        remark?.let { putExtra(TerminalUiCoordinator.EXTRA_REMOTE_PAY_REMARK, it) }
        method?.let { putExtra(TerminalUiCoordinator.EXTRA_REMOTE_PAY_METHOD, it) }
    }

    fun launch(
        context: Context,
        orderId: String,
        amountCents: Long,
        currency: String,
        remark: String?,
        method: String?
    ) {
        val directIntent = build(context, orderId, amountCents, currency, remark, method) ?: return

        if (moveTerminalTaskToFront(context)) {
            Log.i(TAG, "Terminal task moved to front — order=$orderId")
        }

        if (TerminalOverlayWake.isPrivileged(context)) {
            TerminalOverlayWake.launch(context, directIntent)
            Log.i(TAG, "Overlay-privileged wake — order=$orderId")
            return
        }

        Log.w(
            TAG,
            "Display over other apps not granted — cannot wake terminal UI from background order=$orderId"
        )
    }

    private fun moveTerminalTaskToFront(context: Context): Boolean {
        val taskId = TerminalTaskRegistry.taskId
        if (taskId <= 0) return false
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        return try {
            manager.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME)
            true
        } catch (e: Exception) {
            Log.w(TAG, "moveTaskToFront failed taskId=$taskId", e)
            false
        }
    }
}
