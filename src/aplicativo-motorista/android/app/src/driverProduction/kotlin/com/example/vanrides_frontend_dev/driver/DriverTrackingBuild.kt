package com.example.vanrides_frontend_dev.driver

/** No URL, authorization header or030 implementation is present in the MVP source set. */
internal object DriverTrackingBuild {
    const val SPIKE_ENABLED = false
    @Suppress("UNUSED_PARAMETER")
    fun acceptsKind(kind: String): Boolean = false
    @Suppress("UNUSED_PARAMETER")
    fun validToken(value: String): Boolean = false
    @Suppress("UNUSED_PARAMETER")
    fun baseUrl(value: String, emulator: Boolean): String = throw IllegalArgumentException("ACTION_NOT_ALLOWED")
    @Suppress("UNUSED_PARAMETER")
    fun transport(onAvailable: () -> Unit): DriverHttpClient = object : DriverHttpClient {
        override val busy = false
        override fun cancel() = Unit
        override fun execute(credential: DriverCredential, path: String, body: String?, callback: (NativeHttpResult) -> Unit): Boolean {
            throw IllegalStateException("ACTION_NOT_ALLOWED")
        }
    }
}
