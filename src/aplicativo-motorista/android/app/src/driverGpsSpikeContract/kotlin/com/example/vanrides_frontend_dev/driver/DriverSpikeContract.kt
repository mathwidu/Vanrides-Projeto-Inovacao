package com.example.vanrides_frontend_dev.driver

import java.net.URI

/**030-only endpoint policy. Unit fixtures may compile it; MVP never packages it. */
internal object DriverSpikeContract {
    fun baseUrl(value: String, debugEmulator: Boolean): String {
        val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("INVALID_BASE_URL") }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.rawPath.isNullOrEmpty()) && !uri.isOpaque) { "INVALID_BASE_URL" }
        val https = uri.scheme == "https" && uri.host == "gps-spike.vanrides.matheusduarte.dev.br" &&
            (uri.port == -1 || uri.port == 443)
        val local = debugEmulator && uri.scheme == "http" && uri.host == "10.0.2.2" && uri.port == 18765
        require(https || local) { "INVALID_BASE_URL" }
        return if (https) "https://gps-spike.vanrides.matheusduarte.dev.br" else "http://10.0.2.2:18765"
    }

    fun validToken(value: String): Boolean = value.length in 32..512 &&
        value.all { it.code in 33..126 }

}
