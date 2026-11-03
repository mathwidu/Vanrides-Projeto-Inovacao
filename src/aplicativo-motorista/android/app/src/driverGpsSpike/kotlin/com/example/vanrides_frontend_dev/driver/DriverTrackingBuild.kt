package com.example.vanrides_frontend_dev.driver

internal object DriverTrackingBuild {
    const val SPIKE_ENABLED = true
    fun acceptsKind(kind: String): Boolean = kind == "TEST_BEARER"
    fun validToken(value: String): Boolean = DriverSpikeContract.validToken(value)
    fun baseUrl(value: String, emulator: Boolean): String = DriverSpikeContract.baseUrl(value, emulator)
    fun transport(onAvailable: () -> Unit): DriverHttpClient = DriverSpikeHttpClient(onAvailable)
}
