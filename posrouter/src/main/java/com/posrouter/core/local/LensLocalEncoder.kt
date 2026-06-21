package com.posrouter.core.local

import com.posrouter.LocalParamSeparator

/**
 * Mandatory partial encoding for LENS_DATA and Deep Link values.
 * Only structure-breaking characters are escaped; everything else stays plain text.
 * `%` must be encoded first.
 */
internal object LensLocalEncoder {

    fun encode(value: String, separator: LocalParamSeparator = LocalParamSeparator.PIPE): String {
        val withPercent = value.replace("%", "%25")
        var encoded = withPercent
            .replace("=", "%3D")
            .replace("?", "%3F")
            .replace(" ", "%20")
        when (separator) {
            LocalParamSeparator.PIPE -> encoded = encoded.replace("|", "%7C")
            LocalParamSeparator.AMPERSAND -> encoded = encoded.replace("&", "%26")
        }
        return encoded
    }

    internal fun pair(key: String, value: String, separator: LocalParamSeparator): String =
        "$key=${encode(value, separator)}"

    internal fun joinPairs(pairs: List<String>, separator: LocalParamSeparator): String =
        pairs.joinToString(separator.delimiter.toString())
}
