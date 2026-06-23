package com.posrouter.core.lensing

internal object RefundAttemptIdResolver {
    fun defaultAttemptId(orderId: String): String = "$orderId#refund"

    fun resolve(orderId: String, attemptId: String?): String =
        attemptId?.takeIf { it.isNotBlank() } ?: defaultAttemptId(orderId)
}
