package com.posrouter.core.lensing

import com.posrouter.core.registry.AcquirerRouting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object LensingDirectoryClient {
    private const val GATEWAY_MATRIX_URL = "https://lensing.starrie.org/matrix"

    suspend fun fetchRoutingMatrix(
        acquirerCode: String,
        participantCode: String,
        participantKey: String
    ): AcquirerRouting? = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val signature = LensingCrypto.computeSignature(participantKey, timestamp)
            val url = URL("$GATEWAY_MATRIX_URL?code=${acquirerCode.uppercase()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("X-PR-Timestamp", timestamp)
                setRequestProperty("X-PR-Signature", signature)
                setRequestProperty("X-PR-Caller", participantCode.uppercase())
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            if (connection.responseCode != 200) return@withContext null

            val body = connection.inputStream.bufferedReader().readText()
            parseMatrixResponse(body, acquirerCode.uppercase())
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMatrixResponse(body: String, code: String): AcquirerRouting? {
        val json = JSONObject(body)
        val android = json.optJSONObject("android") ?: return null
        val packageName = android.optString("package_name").ifBlank { return null }
        val scheme = android.optString("scheme", code.lowercase()).let {
            if (it.contains("://")) it else "$it://"
        }
        return AcquirerRouting(code = code, packageName = packageName, scheme = scheme)
    }
}
