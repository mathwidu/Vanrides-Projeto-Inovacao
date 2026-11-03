package com.example.vanrides_frontend_dev.driver

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProductionTrackingWireTest {
    private val id = "12345678-1234-1234-1234-123456789012"
    private val route = "/driver-app/v1/tracking-sessions/$id"
    private fun control() = JSONObject().put("sessionId", id).put("generation", 2)
        .put("serverTime", "2026-10-31T12:00:00Z").put("leaseExpiresAt", "2026-10-31T12:05:00Z")
        .put("action", "CONTINUE").put("gpsState", "FRESH").put("stopRequestRef", JSONObject.NULL)

    @Test fun stateRetryRetainsItsKeyButANewHeartbeatGetsANewKey() {
        val wire = ProductionTrackingWire()
        val state = TrackingWire.state(SessionOwner(id, 2), false)
        val first = wire.request("$route/state", state)
        val retry = wire.request("$route/state", state)
        assertEquals(first, retry)
        assertEquals("ACTIVE", JSONObject(first.first!!).getString("state"))
        assertEquals(id, JSONObject(first.first!!).getString("sessionId"))
        wire.response("$route/state", control().toString())
        assertNotEquals(first.second, wire.request("$route/state", state).second)
    }
    @Test fun samplesUseISOClocksAndPartialAckDoesNotDeleteGaps() {
        val owner = SessionOwner(id, 2)
        val queue = SampleQueue()
        (1L..3L).forEach { queue.offer(TrackingSample(it, 1793448000000L, 0.0, 0.0, 5.0, 0), 0) }
        val batch = TrackingWire.batch(owner, queue.batch(0), id, 1793448000000L)
        val wire = ProductionTrackingWire()
        val outgoing = wire.request("$route/samples", batch.body)
        assertEquals(id, outgoing.second)
        assertEquals("2026-10-31T12:00:00.000Z", JSONObject(outgoing.first!!).getString("deviceTime"))
        val ack = control().put("accepted", listOf(1)).put("duplicate", listOf(3)).put("rejected", emptyList<Int>())
        val receipt = TrackingWire.receipt(wire.response("$route/samples", ack.toString()))
        queue.acknowledge(receipt.accepted, receipt.duplicates, receipt.rejected, batch.sequences)
        assertEquals(listOf(2L), queue.batch(0).map { it.sequence })
        assertEquals(owner, receipt.control.owner)
    }
    @Test fun stopEchoesTheRemoteReferenceAfterNativeStopRecord() {
        val wire = ProductionTrackingWire()
        wire.response("$route/state", control().put("stopRequestRef", id).put("action", "STOP").toString())
        val original = TrackingWire.state(SessionOwner(id, 2), true, "REMOTE_STOP", 100, 101)
        val outgoing = JSONObject(wire.request("$route/state", original).first!!)
        assertEquals("STOPPED", outgoing.getString("state"))
        assertEquals(id, outgoing.getString("stopRequestRef"))
        assertEquals(setOf("sessionId", "generation", "state", "stopRequestRef"), outgoing.keys().asSequence().toSet())
    }
    @Test fun lostActivateResponseReusesTheSameIntentKeyAndRunningOwner() {
        val wire = ProductionTrackingWire()
        val path = "/driver-app/v1/start-intents/$id/activate"
        val original = JSONObject().put("expectedVersion", 7).put("foregroundConfirmed", "CONFIRMED")
            .put("intentToken", "A".repeat(43)).toString()
        val first = wire.request(path, original)
        // Transport loses a committed response. Status proves the same session is RUNNING.
        val owner = SessionOwner(id, 2)
        val guard = TrackingActivationGuard()
        guard.observe(RemoteStatus(1000, 301000, owner, "RUNNING", 301000, "STALE"))
        val replay = wire.request(path, original)
        assertEquals(first, replay)
        val control = RemoteControl(owner, 1000, 301000, "CONTINUE", "STALE")
        assertTrue(guard.accepts(control))
        guard.observe(RemoteStatus(2000, 301000, owner, "STOPPED", 2000, "STALE"))
        assertFalse(guard.accepts(control))
    }
    @Test fun pendingStopRestoresOnlyANativeStopAttestation() {
        val owner = SessionOwner(id, 2)
        val original = TrackingWire.state(owner, true, "USER_STOP", 100, 101)
        val saved = TrackingStopJournal.record(owner, original)
        assertEquals(owner to original, TrackingStopJournal.read(JSONObject(saved.toString())))
        for (body in listOf(JSONObject(original).put("latitude", 0).toString(), JSONObject(original).put("state", "ACTIVE").toString())) {
            try { TrackingStopJournal.record(owner, body); fail("Expected closed journal schema") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun linksAreStrictAndOnlyAnExplicitTapCanStart() {
        val valid = "vanridesdriver://run/start#v1.$id.7.${"A".repeat(43)}"
        assertTrue(DriverSessionProtocol.isRunLink(valid))
        assertTrue(DriverSessionProtocol.isRunLink(valid.replace("vanridesdriver://run/start", "https://links.example/motorista/saida"), "links.example"))
        for (invalid in listOf(valid + ".x", valid.replace(".7.", ".07."), valid.replace(".7.", ".9007199254740992."),
            valid.replace("/start#", "/start?x=1#"), valid.replace("v1.", "v2."), valid.dropLast(1) + "B")) {
            assertFalse(DriverSessionProtocol.isRunLink(invalid))
        }
        assertEquals(DriverSessionProtocol.Entry.RUN, DriverSessionProtocol.entry("android.intent.action.VIEW", false, valid))
    }
}
