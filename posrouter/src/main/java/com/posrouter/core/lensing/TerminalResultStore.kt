package com.posrouter.core.lensing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.posrouter.LensingContextHolder
import com.posrouter.PaymentResult
import com.posrouter.RemotePaymentRequest
import com.posrouter.StatusQueryRequest
import com.posrouter.StatusQueryResult
import java.util.Locale

/**
 * Final pay / refund outcomes known to this terminal, kept so status queries can be answered
 * after the in-memory registries (and the process) are gone.
 */
internal object TerminalResultStore {
    private const val TAG = "POSRouter.ResultStore"
    private const val PREFS_NAME = "posrouter_terminal_results"
    private const val KEY_SEPARATOR = "\u001F"
    private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_ENTRIES = 2000

    fun record(result: PaymentResult) {
        val prefs = prefs() ?: return
        val orderId = result.orderId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val attemptId = result.attemptId?.takeIf { it.isNotBlank() }
            ?: PaymentAttemptKey.defaultAttemptId(orderId)
        val metadata = result.metadata - StatusQueryResult.META_QUERY_ID +
            (RemotePaymentRequest.META_OPERATION to operationOf(result)) +
            (StatusQueryResult.META_FINALIZED_AT to System.currentTimeMillis().toString())
        val stored = result.copy(orderId = orderId, attemptId = attemptId, metadata = metadata)
        prefs.edit().putString(key(orderId, attemptId), stored.toJsonString()).apply()
        Log.d(TAG, "Recorded order=$orderId attempt=$attemptId status=${result.status}")
        if (prefs.all.size > MAX_ENTRIES) prune(prefs)
    }

    /** Oldest first; empty when this terminal never finalised [orderId]. */
    fun find(orderId: String, attemptId: String? = null, operation: String? = null): List<PaymentResult> {
        val prefs = prefs() ?: return emptyList()
        val prefix = orderPrefix(orderId.trim())
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return prefs.all.asSequence()
            .filter { (k, _) -> k.startsWith(prefix) }
            .mapNotNull { (_, v) -> (v as? String)?.let { runCatching { PaymentResult.fromJson(it) }.getOrNull() } }
            .filter { finalizedAt(it) >= cutoff }
            .filter { attemptId == null || it.attemptId == attemptId }
            .filter { operation == null || it.metadata[RemotePaymentRequest.META_OPERATION] == operation }
            .sortedBy { finalizedAt(it) }
            .toList()
    }

    private fun operationOf(result: PaymentResult): String {
        val declared = result.metadata[RemotePaymentRequest.META_OPERATION]
        val isRefund = declared.equals(StatusQueryRequest.OPERATION_REFUND, ignoreCase = true) ||
            result.attemptId?.endsWith("#refund") == true
        return if (isRefund) StatusQueryRequest.OPERATION_REFUND else StatusQueryRequest.OPERATION_PAY
    }

    private fun prune(prefs: SharedPreferences) {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val byAge = prefs.all.mapNotNull { (k, v) ->
            val json = v as? String ?: return@mapNotNull k to 0L
            k to (runCatching { finalizedAt(PaymentResult.fromJson(json)) }.getOrDefault(0L))
        }.sortedBy { it.second }
        val overflow = (byAge.size - MAX_ENTRIES).coerceAtLeast(0)
        val editor = prefs.edit()
        byAge.forEachIndexed { index, (k, at) ->
            if (index < overflow || at < cutoff) editor.remove(k)
        }
        editor.apply()
    }

    private fun finalizedAt(result: PaymentResult): Long =
        result.metadata[StatusQueryResult.META_FINALIZED_AT]?.toLongOrNull() ?: 0L

    private fun key(orderId: String, attemptId: String): String = orderPrefix(orderId) + attemptId

    private fun orderPrefix(orderId: String): String = orderId.lowercase(Locale.ROOT) + KEY_SEPARATOR

    private fun prefs(): SharedPreferences? =
        LensingContextHolder.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
