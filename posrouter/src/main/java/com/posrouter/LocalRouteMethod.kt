package com.posrouter

/** How a local connect/pay call reached the acquirer app. */
enum class LocalRouteMethod {
    EXPLICIT_INTENT,
    DEEP_LINK,
    NETWORK
}
