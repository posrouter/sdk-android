package io.starrie.posrouter.core.lensing

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object LensingCrypto {
    fun computeSignature(key: String, timestamp: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal((key + timestamp).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
