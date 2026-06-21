package com.posrouter.core.local

enum class LocalLaunchMethod {
    EXPLICIT_PACKAGE,
    DEEP_LINK
}

data class LocalLaunchResult(
    val success: Boolean,
    val method: LocalLaunchMethod? = null
)
