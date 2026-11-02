package com.example.vanrides_frontend_dev.driver

/** Only the explicitly selected030 source set supplies a bearer transport. */
internal interface DriverHttpClient {
    val busy: Boolean
    fun cancel()
    fun execute(credential: DriverCredential, path: String, body: String?, callback: (NativeHttpResult) -> Unit): Boolean
}
