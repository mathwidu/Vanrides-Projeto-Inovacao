package com.example.vanrides_frontend_dev.driver

internal object TrackingWireChecks {
    private const val control = """{"schemaVersion":1,"environment":"gps-spike","sessionId":"session_A","generation":1,"serverTime":1000000,"leaseExpiresAt":1300000,"action":"CONTINUE","gpsState":"UNKNOWN"}"""
    private fun rejects(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: Exception) { failed = true }
        check(failed) { "Expected invalid wire contract to fail" }
    }
    fun strictControlsAndStatus() {
        check(TrackingWire.control(control).owner == SessionOwner("session_A", 1))
        listOf(control.replace("\"generation\":1", "\"generation\":1.0"), control.replace("\"generation\":1", "\"generation\":0"),
            control.replace("gps-spike", "production"), control.replace("CONTINUE", "RESUME"), control + " {}",
            control.dropLast(1) + ",\"unknown\":true}").forEach { rejects { TrackingWire.control(it) } }
        val status = """{"schemaVersion":1,"environment":"gps-spike","serverTime":1000000,"credentialExpiresAt":1500000,"session":{"sessionId":"session_A","generation":2,"state":"RUNNING","leaseExpiresAt":1300000,"gpsState":"STALE"}}"""
        check(TrackingWire.status(status).owner == SessionOwner("session_A", 2))
        rejects { TrackingWire.status(status.replace("RUNNING", "ACTIVE")) }
        rejects { TrackingWire.status(status.replace("1500000", "1200000")) }
    }
    fun ackAndMissingSequences() {
        val receipt = TrackingWire.receipt(control.dropLast(1) + """, "accepted":[1],"duplicate":[3],"rejected":[{"sequence":4,"code":"TOO_OLD"}]}""")
        check(receipt.accepted == setOf(1L) && receipt.duplicates == setOf(3L) && receipt.rejected == setOf(4L))
        val queue = SampleQueue()
        (1L..4L).forEach { queue.offer(TrackingSample(it, 1_000_000, -29.0, -51.0, 5.0, 0), 0) }
        queue.acknowledge(receipt.accepted, receipt.duplicates, receipt.rejected, setOf(1, 2, 3, 4))
        check(queue.batch(0).single().sequence == 2L)
        rejects { TrackingWire.receipt(control.dropLast(1) + """, "accepted":[1,1],"duplicate":[],"rejected":[]}""") }
    }
    fun byteBoundsAndExactRetryBody() {
        val samples = (1L..30L).map { TrackingSample(it, 1_000_000 + it, -29.0, -51.0, 5.0, 0) }
        val packet = TrackingWire.batch(SessionOwner("session_A", 1), samples, "batch_A", 1_000_100)
        check(packet.body.toByteArray(Charsets.UTF_8).size <= 32768)
        check(packet.sequences.size == 30)
        check(packet.body == TrackingWire.batch(SessionOwner("session_A", 1), samples, "batch_A", 1_000_100).body)
        check(packet.body != TrackingWire.batch(SessionOwner("session_A", 1), samples, "batch_A", 1_000_101).body)
        rejects { TrackingWire.batch(SessionOwner("session_A", 1), samples + samples.first().copy(sequence = 31), "batch_A", 1_000_100) }
        rejects { TrackingWire.batch(SessionOwner("session_A", 1), emptyList(), "batch_A", 1_000_100) }
    }
    @JvmStatic fun main(args: Array<String>) {
        strictControlsAndStatus(); ackAndMissingSequences(); byteBoundsAndExactRetryBody()
        println("PASS: 3 Android JSON scenarios (strict control/status, ACK gaps, batch bounds/exact body)")
    }
}
