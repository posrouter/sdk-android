package com.posrouter

interface POSRouterCallback {
    fun onResult(result: PaymentResult)
    fun onError(error: POSRouterError)
}

data class POSRouterError(
    val code: String,
    val message: String
)
