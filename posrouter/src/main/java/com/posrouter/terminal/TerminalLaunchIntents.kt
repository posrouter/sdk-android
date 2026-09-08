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

    /**
     * Brings the terminal's own task forward for an incoming remote order.
     *
     * Deliberately no MOVE_TASK_WITH_HOME. That flag parks the launcher immediately behind the task
     * it moves, which decides what the operator sees when the terminal steps aside once the order is
     * done: with it, the answer is always the desktop, even though the app the order interrupted —
     * the POS that is waiting on this payment — was the thing on screen a moment earlier. Without
     * it, the stack keeps its own order and receding uncovers whoever was actually there.
     *
     * It went unnoticed for as long as it did because a terminal set as the device Home *is* the
     * home task, so the flag had nothing separate to reposition.
     */
    private fun moveTerminalTaskToFront(context: Context): Boolean {
        val taskId = TerminalTaskRegistry.taskId
        if (taskId <= 0) return false
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        return try {
            manager.moveTaskToFront(taskId, 0)
            true
        } catch (e: Exception) {
            Log.w(TAG, "moveTaskToFront failed taskId=$taskId", e)
            false
        }
    }
}
