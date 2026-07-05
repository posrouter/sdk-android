package com.posrouter

data class POSRouterConfig(
    /** Caller identity, e.g. GPOS */
    val participantCode: String,
    val participantKey: String,
    val terminalId: String,
    /** Partner registry code to pay, e.g. SUPY */
    val acquirerCode: String,
    val merchantId: String,
    /** Platform sub-merchant; omitted on NATS subject as `_`. Must not equal `_`. */
    val subMerchantId: String? = null,
    val callbackUrl: String? = null,
    val currency: String = "NZD",
    /** Optional overrides when Gateway matrix is unavailable */
    val acquirerPackageOverride: String? = null,
    val acquirerSchemeOverride: String? = null,
    /** Local LENS_DATA / deep-link parameter delimiter; default is Lens Protocol v2 pipe. */
    val localParamSeparator: LocalParamSeparator = LocalParamSeparator.PIPE,
    /** Applied when [PaymentRequest.method] is omitted, e.g. `emv_card` for Ezypos card-present. */
    val defaultPayMethod: String? = null,
    /**
     * Optional Gateway origin or `/init` URL for `/init` and `/matrix`.
     * Examples: `https://lensing.posrouter.com`, `https://xxx.vercel.app/init`.
     * When null, uses production default `https://lensing.starrie.org/init`.
     */
    val gatewayBaseUrl: String? = null,
    /**
     * B-side payment terminal: NATS runs under a foreground service and survives UI teardown.
     * A-side integrators (GoMenu, …) leave this `false` (default).
     */
    val terminalMode: Boolean = false,
    /**
     * Fully-qualified activity class launched when a remote pay arrives while UI is inactive.
     * Required when [terminalMode] is `true` (e.g. `com.posrouter.kiosk.KioskActivity`).
     */
    val terminalLaunchActivityClass: String? = null
)
