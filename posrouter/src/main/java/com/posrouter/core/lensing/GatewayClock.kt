package com.posrouter.core.lensing

internal const val CLOCK_SKEW = "CLOCK_SKEW"

internal fun gatewayException(
    statusCode: Int,
    errorBody: String,
    localNowMs: Long = System.currentTimeMillis()
): LensingException {
    if (statusCode == 401 && isClockSkewBody(errorBody)) {
        return LensingException(CLOCK_SKEW, clockSkewMessage(errorBody, localNowMs))
    }
    return LensingException("GATEWAY_ERROR", errorBody.ifBlank { "HTTP $statusCode" })
}

internal fun isClockSkewBody(errorBody: String): Boolean {
    val code = jsonStringField(errorBody, "code")
    if (code == CLOCK_SKEW) return true
    val error = jsonStringField(errorBody, "error")
    if (error != null) return error.contains("timestamp", ignoreCase = true)
    return errorBody.contains("timestamp", ignoreCase = true)
}

internal fun clockSkewMessage(errorBody: String, localNowMs: Long): String {
    val serverTime = jsonLongField(errorBody, "server_time")
    if (serverTime == null || serverTime <= 0L) {
        return "This device's clock is outside the allowed window. Sign-in is refused until the date and time are corrected."
    }
    val skewMs = localNowMs - serverTime
    val minutes = (kotlin.math.abs(skewMs) / 60_000L).coerceAtLeast(1L)
    val direction = if (skewMs > 0L) "ahead" else "behind"
    return "This device's clock is about $minutes minutes $direction. Sign-in is refused until the date and time are corrected."
}

private fun jsonStringField(body: String, name: String): String? {
    val match = Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""").find(body) ?: return null
    return match.groupValues[1]
}

private fun jsonLongField(body: String, name: String): Long? {
    val match = Regex(""""$name"\s*:\s*(-?\d+)""").find(body) ?: return null
    return match.groupValues[1].toLongOrNull()
}
