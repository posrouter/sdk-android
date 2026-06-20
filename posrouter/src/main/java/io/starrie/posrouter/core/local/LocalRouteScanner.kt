package io.starrie.posrouter.core.local

import android.content.Context
import android.content.pm.PackageManager

internal object LocalRouteScanner {
    fun checkAcquirerInstalled(context: Context, targetPackageName: String): Boolean {
        if (targetPackageName.isBlank()) return false
        return try {
            context.packageManager.getPackageInfo(targetPackageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
