package com.example.vanrides_frontend_dev.driver

import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Closed wire rules for Spring040. This protocol never configures the spike collector. */
internal object DriverSessionProtocol {
    const val CHANNEL = "dev.matheusduarte.vanrides.driver/session.v1"
    const val LAB_ORIGIN = "https://localhost:18443"
    const val RETURN_URI = "vanridesdriver://pairing/return"
    const val INVALID_RETURN_URI = "vanridesdriver://invalid/return"
    const val HTTPS_RETURN_PATH = "/motorista/vinculo/retorno"
    const val PREFIX = "/driver-app/v1"
    const val MAX_BYTES = 32768
    val states = setOf("UNCONFIGURED", "UNPAIRED", "REQUESTING", "PENDING", "SETUP_REQUIRED", "READY", "EXPIRED", "REVOKING", "REVOKED")
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    fun origin(value: String): String {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.isNullOrEmpty() &&
            !uri.isOpaque && uri.port in -1..65535 && uri.port != 0 &&
            value == "https://${uri.rawAuthority}") { "INVALID_CONFIGURATION" }
        return value
    }
    fun isReturn(value: String?, linkHost: String = ""): Boolean = value == RETURN_URI ||
        linkHost.isNotEmpty() && value == "https://$linkHost$HTTPS_RETURN_PATH"
    fun isRunLink(value: String?, linkHost: String = ""): Boolean {
        if (value == null || value.contains('%')) return false
        val match = Regex("(?:vanridesdriver://run/start|" + Regex.escape("https://$linkHost/motorista/saida") + ")#v1\\.([0-9a-f-]{36})\\.(0|[1-9][0-9]{0,15})\\.([A-Za-z0-9_-]{43})").matchEntire(value) ?: return false
        return try { uuid(match.groupValues[1]); token(match.groupValues[3]); match.groupValues[2].toLong() <= 9007199254740991L } catch (_: Exception) { false }
    }
    enum class Entry { ICON, RETURN, RUN, INVALID }
    fun entry(action: String?, launcher: Boolean, uri: String?, linkHost: String = ""): Entry = when {
        action == "android.intent.action.MAIN" && launcher && uri == null -> Entry.ICON
        action == "android.intent.action.VIEW" && isReturn(uri, linkHost) -> Entry.RETURN
        action == "android.intent.action.VIEW" && isRunLink(uri, linkHost) -> Entry.RUN
        else -> Entry.INVALID
    }
    fun fields(value: JSONObject, required: Set<String>, optional: Set<String> = emptySet()) {
        val keys = value.keys().asSequence().toSet()
        require(keys.containsAll(required) && keys.all { it in required || it in optional }) { "INVALID_CONTRACT" }
    }
    fun string(value: JSONObject, name: String): String = (value.get(name) as? String)
        ?: throw IllegalArgumentException("INVALID_CONTRACT")
    fun uuid(value: String): String = value.also {
        require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(it)) { "INVALID_CONTRACT" }
    }
    fun code(value: String): String = value.also { require(Regex("[BCDFGHJKMNPQRSTVWXYZ]{8}").matches(it)) { "INVALID_CONTRACT" } }
    fun b64(bytes: ByteArray): String {
        val result = StringBuilder()
        var bits = 0; var count = 0
        for (byte in bytes) {
            bits = (bits shl 8) or (byte.toInt() and 255); count += 8
            while (count >= 6) { count -= 6; result.append(alphabet[(bits ushr count) and 63]) }
        }
        if (count > 0) result.append(alphabet[(bits shl (6 - count)) and 63])
        return result.toString()
    }
    fun unb64(value: String): ByteArray {
        require(value.isNotEmpty() && value.length % 4 != 1 && value.all { it in alphabet }) { "INVALID_CONTRACT" }
        val output = java.io.ByteArrayOutputStream()
        var bits = 0; var count = 0
        value.forEach { char ->
            bits = (bits shl 6) or alphabet.indexOf(char); count += 6
            if (count >= 8) { count -= 8; output.write((bits ushr count) and 255) }
        }
        return output.toByteArray().also { require(b64(it) == value) { "INVALID_CONTRACT" } }
    }
    fun token(value: String): String = value.also { require(it.length == 43 && unb64(it).size == 32) { "INVALID_CONTRACT" } }
    fun hash(bytes: ByteArray): String = b64(MessageDigest.getInstance("SHA-256").digest(bytes))
    fun coordinate(value: BigInteger): String {
        val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        require(bytes.size <= 32)
        return b64(ByteArray(32 - bytes.size) + bytes)
    }
    fun instant(value: String): Long {
        require(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z").matches(value)) { "INVALID_CONTRACT" }
        val whole = value.substring(0, 19) + "Z"
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            isLenient = false; timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        val time = parser.parse(whole, position) ?: throw IllegalArgumentException("INVALID_CONTRACT")
        require(position.index == whole.length)
        val fraction = if (value.length > 20) value.substring(20, value.length - 1).padEnd(3, '0').take(3).toLong() else 0
        return time.time + fraction
    }
    fun iso(time: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(time))

    /** AndroidKeyStore emits ASN.1 DER; JWS ES256 requires fixed-width unsigned R || S. */
    fun joseSignature(der: ByteArray): ByteArray {
        var cursor = 0
        fun next(): Int { require(cursor < der.size); return der[cursor++].toInt() and 255 }
        fun length(): Int {
            val first = next()
            if (first < 128) return first
            require(first == 129); return next().also { require(it >= 128) }
        }
        require(next() == 48)
        val sequence = length(); require(sequence == der.size - cursor)
        fun integer(): ByteArray {
            require(next() == 2)
            val size = length(); require(size in 1..33 && cursor + size <= der.size)
            val raw = der.copyOfRange(cursor, cursor + size); cursor += size
            require(raw[0].toInt() and 128 == 0)
            if (raw.size > 1 && raw[0] == 0.toByte()) require(raw[1].toInt() and 128 != 0)
            val number = raw.dropWhile { it == 0.toByte() }.toByteArray()
            require(number.size <= 32)
            return ByteArray(32 - number.size) + number
        }
        val r = integer(); val s = integer(); require(cursor == der.size)
        return r + s
    }
}

internal data class NativeHttpResult(val status: Int, val body: String?, val requestStarted: Long, val failure: String? = null)

internal class DriverSessionFailure(val code: String) : Exception(code, null, false, false)
internal interface DriverSessionKeys {
    fun exists(): Boolean
    fun create()
    fun publicJwk(): JSONObject
    fun sign(bytes: ByteArray): ByteArray
}
internal interface DriverSessionStore {
    fun load(): JSONObject?
    fun save(value: JSONObject)
}
internal class DriverSessionRequest(val method: String, val path: String, val body: ByteArray?,
    val proof: String, val accessToken: String?, val idempotencyKey: String?)
internal data class DriverSessionResponse(val status: Int, val body: JSONObject, val nonce: String? = null)
internal fun interface DriverSessionTransport { fun send(request: DriverSessionRequest): DriverSessionResponse }
