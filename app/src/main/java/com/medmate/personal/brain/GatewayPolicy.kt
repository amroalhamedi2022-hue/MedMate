package com.medmate.personal.brain

import java.net.URI

/** Strictly HTTPS, explicit user-configured gateway. Never silently upload PDF or requests. */
object GatewayPolicy {
    /** Session-only bearer credential: reject CR/LF, controls and Unicode
     * header ambiguity before passing the value to HttpURLConnection.
     * An empty token is valid for a gateway that does not require auth.
     */
    fun validSessionToken(value: String): Boolean =
        value.length <= 2048 && value.all { it.code in 0x21..0x7e }

    fun endpoint(value: String): String {
        val uri = try { URI(value.trim()) } catch (_: Exception) { throw IllegalArgumentException("رابط بوابة AI غير صالح") }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.fragment == null && uri.rawQuery == null &&
            uri.path.endsWith("/v1/chat/completions") && uri.port in listOf(-1, 443, 8443)) {
            "لازم رابط HTTPS ينتهي بـ /v1/chat/completions، بدون مفاتيح في الرابط"
        }
        return uri.toASCIIString()
    }
}
