package com.example.vanrides_frontend_dev.driver

/** Also executable with the cached Kotlin compiler, without Gradle/Flutter or generated files. */
internal object TrackingProtocolChecks {
    private fun rejects(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true }
        check(rejected) { "Expected rejection" }
    }
    fun endpointIsolation() {
        val public = "https://gps-spike.vanrides.matheusduarte.dev.br"
        check(DriverSpikeContract.baseUrl(public, false) == public)
        check(DriverSpikeContract.baseUrl("$public:443", false) == public)
        check(DriverSpikeContract.baseUrl("http://10.0.2.2:18765", true) == "http://10.0.2.2:18765")
        listOf("http://10.0.2.2:18765", "http://127.0.0.1:18765", "$public/", "$public/path", "$public?x=1",
            "$public#x", "https://user@gps-spike.vanrides.matheusduarte.dev.br", "$public:444", "https://example.org").forEach {
            rejects { DriverSpikeContract.baseUrl(it, false) }
        }
        check(!DriverSpikeContract.validToken("secret\r\nHeader: value".padEnd(43, 'x')))
        check(!DriverSpikeContract.validToken("x".repeat(31)))
        check(DriverSpikeContract.validToken("x".repeat(43)))
    }
    fun leaseConservativeAndReplay() {
        val lease = MonotonicLease()
        lease.update(1_000_000, 1_300_000, 2_000, 2_150, 2_000_000)
        check(lease.deadline == 302_000L && lease.remaining(2_150) == 299_850L)
        lease.update(1_000_000, 1_300_000, 22_000, 22_100, 2_000_000)
        check(lease.deadline == 302_000L) // delayed/replayed ACK cannot extend this lease
        lease.update(1_020_000, 1_320_000, 22_200, 22_300, 2_000_000)
        check(lease.deadline == 322_200L)
        rejects { lease.update(1_000_000, 1_300_000, 23_000, 23_050, 2_000_000) }
        rejects { lease.update(1_400_000, 1_700_000, 400_000, 400_010, 2_000_000) }
        check(lease.remaining(400_000) == 0L) // elapsed clock, including sleep, never resumes a lease
    }
    fun leaseBoundsAndCredential() {
        rejects { MonotonicLease().update(1_000_000, 1_300_001, 0, 1, 2_000_000) }
        rejects { MonotonicLease().update(1_000_000, 1_300_000, 0, 1, 1_299_999) }
        rejects { MonotonicLease().update(1_000_000, 1_000_000, 0, 0, 2_000_000) }
        rejects { MonotonicLease().update(1_000_000, 1_000_100, 0, 101, 2_000_000) }
        rejects { MonotonicLease().update(1_000_000, 1_000_100, 1_000, 900, 2_000_000) }
    }
    private fun sample(sequence: Long, elapsed: Long = 0) = TrackingSample(sequence, 1_000_000 + sequence, -29.0, -51.0, 5.0, elapsed)
    fun boundedQueueAndAge() {
        val queue = SampleQueue()
        (1L..121L).forEach { queue.offer(sample(it), 0) }
        check(queue.size(0) == 120 && queue.dropped == 1 && queue.batch(0).first().sequence == 2L)
        check(queue.batch(0).size == 30)
        check(queue.size(119_999) == 120)
        check(queue.size(120_000) == 0 && queue.dropped == 121)
        queue.offer(sample(122, 0), 120_001)
        check(queue.size(120_001) == 0 && queue.dropped == 122)
        queue.offer(sample(123, 130_000), 130_000)
        queue.offer(sample(124, 120_000), 130_000) // out-of-order capture still expires independently
        check(queue.size(240_000) == 1 && queue.batch(240_000).single().sequence == 123L)
    }
    fun explicitAcknowledgmentsOnly() {
        val queue = SampleQueue()
        (1L..4L).forEach { queue.offer(sample(it), 0) }
        queue.acknowledge(setOf(1), setOf(3), emptySet(), setOf(1, 2, 3, 4))
        check(queue.batch(0).map { it.sequence } == listOf(2L, 4L))
        rejects { queue.acknowledge(setOf(2), setOf(2), emptySet(), setOf(2, 4)) }
        rejects { queue.acknowledge(setOf(99), emptySet(), emptySet(), setOf(2, 4)) }
        check(queue.size(0) == 2)
        queue.acknowledge(emptySet(), emptySet(), setOf(4), setOf(2, 4))
        check(queue.batch(0).single().sequence == 2L)
    }
    fun ownerAndFiniteNumbers() {
        rejects { SessionOwner("has/slash", 1) }
        rejects { SessionOwner("x".repeat(81), 1) }
        rejects { SessionOwner("session", 0) }
        check(SessionOwner("session", 1) != SessionOwner("session", 2))
        check(SessionOwner("session", 1) != SessionOwner("other", 1))
        val queue = SampleQueue()
        queue.offer(sample(1).copy(latitude = Double.NaN), 0)
        queue.offer(sample(2).copy(longitude = 181.0), 0)
        queue.offer(sample(3).copy(accuracyM = Double.POSITIVE_INFINITY), 0)
        queue.offer(sample(4).copy(speedMps = 151.0), 0)
        queue.offer(sample(5).copy(headingDeg = 360.0), 0)
        check(queue.size(0) == 0 && queue.dropped == 5)
        check(TrackingProtocol.fatalHttp(409) && !TrackingProtocol.retryHttp(409))
        check(TrackingProtocol.retryHttp(503) && TrackingProtocol.retryHttp(429))
    }
    fun stopFailureClassification() {
        check(TrackingProtocol.failureCode(0, "INVALID_CONTRACT") == "INVALID_CONTRACT")
        check(TrackingProtocol.failureCode(200, "INVALID_CONTRACT") == "INVALID_CONTRACT")
        check(TrackingProtocol.failureCode(0, "NETWORK_ERROR") == "NETWORK_RETRY")
        check(TrackingProtocol.failureCode(0, "NETWORK_TIMEOUT") == "NETWORK_RETRY")
        check(TrackingProtocol.failureCode(503, null) == "NETWORK_RETRY")
        check(TrackingProtocol.failureCode(429, null) == "NETWORK_RETRY")
        check(TrackingProtocol.failureCode(401, null) == "HTTP_401")
        check(TrackingProtocol.failureCode(302, "REDIRECT_REJECTED") == "REDIRECT_REJECTED")
        check(TrackingProtocol.failureCode(0, null) == "INVALID_CONTRACT")
    }
    @JvmStatic fun main(args: Array<String>) {
        endpointIsolation(); leaseConservativeAndReplay(); leaseBoundsAndCredential()
        boundedQueueAndAge(); explicitAcknowledgmentsOnly(); ownerAndFiniteNumbers()
        stopFailureClassification()
        println("PASS: 7 Android protocol scenarios (URL, replay/lease, bounds, queue age/cap, ACK gaps, owner/numbers, stop retry)")
    }
}
