package com.posrouter.core.registry

data class AcquirerRouting(
    val code: String,
    val packageName: String,
    val scheme: String
) {
    val schemeUri: String
        get() = if (scheme.contains("://")) scheme else "$scheme://"
}
