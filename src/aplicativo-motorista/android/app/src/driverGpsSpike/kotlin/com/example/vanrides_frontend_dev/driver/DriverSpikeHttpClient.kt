package com.example.vanrides_frontend_dev.driver

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


/** A single executor owns every connection. Cancellation cannot allow a second request to overlap. */
internal class DriverSpikeHttpClient(private val onAvailable: () -> Unit) : DriverHttpClient {
    private class Flight {
        val connection = AtomicReference<HttpURLConnection?>()
        val result = AtomicReference<NativeHttpResult?>()
        val cancelled = AtomicBoolean(false)
    }
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private var flight: Flight? = null
    override val busy: Boolean get() = flight != null
    override fun cancel() {
        flight?.let { current ->
            current.cancelled.set(true)
            timer.execute { current.connection.get()?.disconnect() }
        }
    }
    override fun execute(credential: DriverCredential, path: String, body: String?, callback: (NativeHttpResult) -> Unit): Boolean {
        if (busy) return false
        val current = Flight(); flight = current
        val started = SystemClock.elapsedRealtime()
        fun deliver(result: NativeHttpResult) {
            // Record once, but notify only after the executor releases its connection.
            // A callback may immediately start the next request (status -> activate).
            current.result.compareAndSet(null, result)
        }
        val timeout = timer.schedule({
            current.cancelled.set(true)
            deliver(NativeHttpResult(0, null, started, "NETWORK_TIMEOUT"))
            current.connection.get()?.disconnect()
        }, TrackingProtocol.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                if (current.cancelled.get()) throw java.io.IOException()
                connection = URL(credential.baseUrl + path).openConnection() as HttpURLConnection
                current.connection.set(connection)
                if (current.cancelled.get()) throw java.io.IOException()
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TrackingProtocol.REQUEST_TIMEOUT_MS.toInt()
                connection.readTimeout = TrackingProtocol.REQUEST_TIMEOUT_MS.toInt()
                connection.useCaches = false
                connection.requestMethod = if (body == null) "GET" else "POST"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${credential.token}")
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    require(bytes.size <= TrackingProtocol.MAX_BODY_BYTES)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                if (code in 300..399) deliver(NativeHttpResult(code, null, started, "REDIRECT_REJECTED"))
                else if (code in 200..299) {
                    val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                    require(contentType == "application/json")
                    val bytes = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val chunk = ByteArray(2048)
                        while (output.size() <= TrackingProtocol.MAX_BODY_BYTES) {
                            val count = input.read(chunk, 0, minOf(chunk.size, TrackingProtocol.MAX_BODY_BYTES + 1 - output.size()))
                            if (count < 0) break
                            output.write(chunk, 0, count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size <= TrackingProtocol.MAX_BODY_BYTES)
                    deliver(NativeHttpResult(code, String(bytes, Charsets.UTF_8), started))
                } else deliver(NativeHttpResult(code, null, started))
            } catch (_: IllegalArgumentException) {
                deliver(NativeHttpResult(0, null, started, "INVALID_CONTRACT"))
            } catch (_: Exception) {
                deliver(NativeHttpResult(0, null, started, if (current.cancelled.get()) "NETWORK_CANCELLED" else "NETWORK_ERROR"))
            } finally {
                timeout.cancel(false)
                try { connection?.disconnect() } catch (_: Exception) { }
                main.post {
                    if (flight === current) {
                        flight = null
                        callback(current.result.get() ?: NativeHttpResult(0, null, started, "NETWORK_ERROR"))
                        onAvailable()
                    }
                }
            }
        }
        return true
    }
}
