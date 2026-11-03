package com.example.vanrides_frontend_dev.driver

/** Platform independent safety rules; no credentials or coordinates in exceptions. */
internal object TrackingProtocol {
    const val MAX_LEASE_MS = 300_000L
    const val MAX_QUEUE_AGE_MS = 120_000L
    const val MAX_QUEUE = 120
    const val MAX_BATCH = 30
    const val MAX_BODY_BYTES = 32_768
    const val REQUEST_TIMEOUT_MS = 8_000L
    private val opaque = Regex("[A-Za-z0-9_-]{1,80}")
    fun id(value: String): String = value.also { require(opaque.matches(it)) { "INVALID_ID" } }

    fun fatalHttp(code: Int): Boolean = code in setOf(401, 403, 404, 409, 410)
    fun retryHttp(code: Int): Boolean = code == 429 || code in 500..599
    fun failureCode(status: Int, failure: String?): String = when {
        // A malformed successful response has status 0 in the HTTP adapter.
        // Its explicit failure must win over transport retry classification.
        failure == "INVALID_CONTRACT" -> "INVALID_CONTRACT"
        failure == "REDIRECT_REJECTED" || status in 300..399 -> "REDIRECT_REJECTED"
        failure in setOf("NETWORK_ERROR", "NETWORK_TIMEOUT", "NETWORK_CANCELLED") -> "NETWORK_RETRY"
        failure != null -> "INVALID_CONTRACT"
        fatalHttp(status) -> "HTTP_$status"
        retryHttp(status) -> "NETWORK_RETRY"
        else -> "INVALID_CONTRACT"
    }
}

internal data class SessionOwner(val id: String, val generation: Long) {
    init { TrackingProtocol.id(id); require(generation >= 1) { "INVALID_GENERATION" } }
}

/** elapsedRealtime includes deep sleep. Replayed leases never buy additional time. */
internal class MonotonicLease {
    var expiresAt: Long? = null
        private set
    var deadline: Long = 0
        private set
    fun clear() { expiresAt = null; deadline = 0 }
    fun remaining(now: Long): Long = (deadline - now).coerceAtLeast(0)
    fun update(serverTime: Long, expires: Long, requestStarted: Long, now: Long, credentialExpiry: Long) {
        val duration = expires - serverTime
        require(serverTime > 0 && duration in 1..TrackingProtocol.MAX_LEASE_MS &&
            expires <= credentialExpiry && requestStarted <= now) { "INVALID_LEASE" }
        val proposed = requestStarted + duration
        if (expiresAt != null) {
            require(remaining(now) > 0) { "LEASE_EXPIRED" }
            require(expires >= expiresAt!!) { "OLD_LEASE" }
        }
        deadline = if (expiresAt == expires) minOf(deadline, proposed) else proposed
        expiresAt = expires
        require(remaining(now) > 0) { "LEASE_EXPIRED" }
    }
}

internal data class TrackingSample(
    val sequence: Long, val capturedAt: Long, val latitude: Double, val longitude: Double,
    val accuracyM: Double, val receivedElapsed: Long, val speedMps: Double? = null,
    val headingDeg: Double? = null,
) {
    fun valid(): Boolean = sequence >= 1 && capturedAt > 0 && latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 && accuracyM.isFinite() && accuracyM in 0.0..10000.0 &&
        (speedMps == null || speedMps.isFinite() && speedMps in 0.0..150.0) &&
        (headingDeg == null || headingDeg.isFinite() && headingDeg >= 0 && headingDeg < 360)
}

internal class SampleQueue {
    private val samples = ArrayDeque<TrackingSample>()
    var dropped = 0
        private set
    fun purge(now: Long) {
        val before = samples.size
        samples.removeAll { now - it.receivedElapsed >= TrackingProtocol.MAX_QUEUE_AGE_MS }
        dropped += before - samples.size
    }
    fun offer(sample: TrackingSample, now: Long) {
        purge(now)
        if (!sample.valid() || sample.receivedElapsed > now || now - sample.receivedElapsed >= TrackingProtocol.MAX_QUEUE_AGE_MS) { dropped++; return }
        while (samples.size >= TrackingProtocol.MAX_QUEUE) { samples.removeFirst(); dropped++ }
        samples.addLast(sample)
    }
    fun batch(now: Long): List<TrackingSample> { purge(now); return samples.take(TrackingProtocol.MAX_BATCH) }
    fun size(now: Long): Int { purge(now); return samples.size }
    fun containsAll(sequences: Set<Long>, now: Long): Boolean {
        purge(now); return samples.map { it.sequence }.toSet().containsAll(sequences)
    }
    fun acknowledge(accepted: Set<Long>, duplicate: Set<Long>, rejected: Set<Long>, requested: Set<Long>) {
        require(accepted.intersect(duplicate).isEmpty() && accepted.intersect(rejected).isEmpty() &&
            duplicate.intersect(rejected).isEmpty() && requested.containsAll(accepted + duplicate + rejected)) { "INVALID_ACK" }
        val confirmed = accepted + duplicate + rejected
        samples.removeAll { it.sequence in confirmed }
    }
    fun clear() { samples.clear() }
}
