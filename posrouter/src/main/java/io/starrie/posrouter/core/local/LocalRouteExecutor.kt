package io.starrie.posrouter.core.local

import android.app.Activity
import android.content.Intent
import android.net.Uri
import io.starrie.posrouter.PaymentRequest

internal object LocalRouteExecutor {
    fun launchViaIntent(activity: Activity, request: PaymentRequest) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(request.targetPackageName)
            data = Uri.parse(
                "posrouter://pay?terminalId=${request.terminalId}&amount=${request.amount}&currency=${request.currency}"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            val fallbackIntent = activity.packageManager.getLaunchIntentForPackage(request.targetPackageName)
            fallbackIntent?.let { activity.startActivity(it) }
        }
    }
}
