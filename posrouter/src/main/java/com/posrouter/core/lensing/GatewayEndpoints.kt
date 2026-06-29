package com.posrouter.core.lensing

import com.posrouter.POSRouterConfig

internal object GatewayEndpoints {
    const val DEFAULT_INIT_URL = "https://gateway.posrouter.com/init"
    val DEFAULT_MATRIX_URL: String
        get() = DEFAULT_INIT_URL.replace(Regex("/init$", RegexOption.IGNORE_CASE), "/matrix")

    fun initUrl(config: POSRouterConfig): String = resolveInit(config.gatewayBaseUrl)

    fun matrixUrl(config: POSRouterConfig): String =
        resolveInit(config.gatewayBaseUrl).replace(Regex("/init$", RegexOption.IGNORE_CASE), "/matrix")

    fun resolveInit(gatewayBaseUrl: String?): String {
        val raw = gatewayBaseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_INIT_URL
        return when {
            raw.endsWith("/init", ignoreCase = true) -> raw
            raw.endsWith("/matrix", ignoreCase = true) ->
                raw.replace(Regex("/matrix$", RegexOption.IGNORE_CASE), "/init")
            raw.endsWith("/") -> "${raw}init"
            else -> "$raw/init"
        }
    }
}
