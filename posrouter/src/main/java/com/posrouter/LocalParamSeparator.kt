package com.posrouter

/**
 * Parameter delimiter for local LENS_DATA and deep-link query strings.
 * Legacy acquirers (e.g. current Ezypos builds) use [AMPERSAND]; Lens Protocol v2 uses [PIPE].
 */
enum class LocalParamSeparator {
    PIPE,
    AMPERSAND;

    internal val delimiter: Char
        get() = when (this) {
            PIPE -> '|'
            AMPERSAND -> '&'
        }
}
