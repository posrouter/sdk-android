package com.posrouter.core.lensing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object LensingGatewayClient {
    suspend fun fetchNatsCredentials(
        code: String,
        key: String,
        initUrl: String = GatewayEndpoints.DEFAULT_INIT_URL
    ): GatewayResponse =
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis().toString()
            val signature = LensingCrypto.computeSignature(key, timestamp)

            val url = URL("$initUrl?code=$code")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("X-PR-Timestamp", timestamp)
                setRequestProperty("X-PR-Signature", signature)
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            val statusCode = connection.responseCode
            if (statusCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP $statusCode"
                throw LensingException("GATEWAY_ERROR", errorBody)
            }

            val body = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            GatewayResponse(
                natsUrl = json.getString("nats_url"),
                natsToken = json.getString("nats_token")
            )
        }
}

data class GatewayResponse(
    val natsUrl: String,
    val natsToken: String
)

class LensingException(val code: String, message: String) : Exception(message)
