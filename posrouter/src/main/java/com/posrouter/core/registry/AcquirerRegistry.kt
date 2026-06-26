package com.posrouter.core.registry

import com.posrouter.POSRouterConfig
import com.posrouter.core.lensing.GatewayEndpoints
import com.posrouter.core.lensing.LensingDirectoryClient
import java.util.concurrent.ConcurrentHashMap

internal object AcquirerRegistry {

    private val routingCache = ConcurrentHashMap<String, AcquirerRouting>()

    private val bakedDefaults = mapOf(
        "SUPY" to AcquirerRouting("SUPY", "ezypay.com.globe.cardpos", "ezypos://")
    )

    fun resolve(config: POSRouterConfig, attemptCode: String? = null): AcquirerRouting {
        val code = (attemptCode ?: config.acquirerCode).uppercase()
        val overridePackage = config.acquirerPackageOverride
        if (!overridePackage.isNullOrBlank() && attemptCode.isNullOrBlank()) {
            return AcquirerRouting(
                code = code,
                packageName = overridePackage,
                scheme = config.acquirerSchemeOverride ?: "ezypos://"
            )
        }
        return routingCache[code]
            ?: bakedDefaults[code]
            ?: AcquirerRouting(code = code, packageName = "", scheme = "${code.lowercase()}://")
    }

    suspend fun prefetch(config: POSRouterConfig) {
        val routing = LensingDirectoryClient.fetchRoutingMatrix(
            acquirerCode = config.acquirerCode,
            participantCode = config.participantCode,
            participantKey = config.participantKey,
            matrixUrl = GatewayEndpoints.matrixUrl(config)
        ) ?: return
        routingCache[config.acquirerCode.uppercase()] = routing
    }
}
