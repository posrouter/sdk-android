package com.posrouter

data class POSRouterConfig(
    /** Caller identity, e.g. GPOS */
    val participantCode: String,
    val participantKey: String,
    val terminalId: String,
    /** Partner registry code to pay, e.g. SUPY */
    val acquirerCode: String,
    val merchantId: String,
    val callbackUrl: String? = null,
    val currency: String = "NZD",
    /** Optional overrides when Gateway matrix is unavailable */
    val acquirerPackageOverride: String? = null,
    val acquirerSchemeOverride: String? = null,
    /** Local LENS_DATA / deep-link parameter delimiter; default is Lens Protocol v2 pipe. */
    val localParamSeparator: LocalParamSeparator = LocalParamSeparator.PIPE,
    /** Applied when [PaymentRequest.method] is omitted, e.g. `emv_card` for Ezypos card-present. */
    val defaultPayMethod: String? = null
)
