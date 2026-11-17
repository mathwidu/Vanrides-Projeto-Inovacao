package com.example.vanrides_frontend_dev.driver

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DriverFinishFlowTest {
    private val id = "12345678-1234-1234-1234-123456789012"
    private val run = "12345678-1234-1234-1234-123456789013"
    private val session = "12345678-1234-1234-1234-123456789014"
    private val ref = "12345678-1234-1234-1234-123456789015"
    private fun auth() = JSONObject().put("intentId",id).put("expectedVersion",7).put("intentToken","A".repeat(43))
    private fun preview() = JSONObject().put("intentId",id).put("runId",run).put("trackingSessionId",session)
        .put("sessionGeneration",2).put("expectedVersion",7).put("expiresAt","2030-01-01T18:05:00Z")
        .put("state","READY").put("stopAcknowledged",false).put("trip",JSONObject().put("name","Faculdade sintética")
            .put("direction","RETURN").put("localDate","2030-01-01").put("localTime","18:00:00").put("timezone","America/Sao_Paulo"))
    private fun receipt(ack: Boolean) = JSONObject().put("intentId",id).put("runId",run).put("trackingSessionId",session)
        .put("sessionGeneration",2).put("expectedVersion",7).put("state","COMPLETED").put("finishConsumedAt","2030-01-01T18:00:01Z")
        .put("stopAcknowledged",ack).put("stopRequestRef",ref).put("stoppedAt",if (ack) "2030-01-01T18:00:02Z" else JSONObject.NULL)
    private fun confirmed(ack: Boolean) = JSONObject().put("receipt",receipt(ack)).put("control",JSONObject()
        .put("runId",run).put("state","COMPLETED").put("entityVersion",8).put("trackingSessionId",session)
        .put("generation",2).put("serverTime","2030-01-01T18:00:01Z").put("action","STOP").put("gpsState","STALE")
        .put("leaseExpiresAt","2030-01-01T18:05:00Z").put("stopRequestRef",ref))
    private data class Request(val path: String,val body: String,val key: String?,val reply: (NativeHttpResult)->Unit)
    private inner class Fixture(initial: JSONObject? = null, ownerPresent: Boolean = true) {
        var saved = initial
        var off = false
        var now = 0L
        var currentOwner: SessionOwner? = if (ownerPresent) SessionOwner(session,2) else null
        var pendingStop: String? = null
        var durableStop: String? = null
        val trace = mutableListOf<String>()
        val requests = mutableListOf<Request>()
        val flow = DriverFinishFlow(
            request = { path,body,key,reply -> requests.add(Request(path,body,key,reply)); trace.add(path) },
            load = { saved }, save = { saved = it?.let { JSONObject(it.toString()) }; trace.add("persist") },
            localOwner = { currentOwner },localOff = { off },available = { true },
            stopLocal = { off=true;trace.add("provider-off") },
            prepareStop = { expected ->
                if (pendingStop == null) pendingStop = DriverFinishContract.stoppedPacket(expected,null,currentOwner,off,1_893_520_800_000L)
                durableStop = pendingStop; currentOwner = expected;trace.add("prepare-stop")
            },
            bindStop = { owner,value ->
                pendingStop = DriverFinishContract.stoppedPacket(owner,value,currentOwner,off,1_893_520_800_000L)
                durableStop = pendingStop
                currentOwner = owner;assertEquals(ref,value);trace.add("bind-stop")
            },
            clearStop = { owner,value ->
                assertTrue(off);assertEquals(SessionOwner(session,2),owner);assertEquals(ref,value)
                pendingStop?.let { if (JSONObject(it).has("stopRequestRef")) assertEquals(ref,JSONObject(it).getString("stopRequestRef")) }
                pendingStop=null;durableStop=null;trace.add("clear-exact-ack")
            },
            wake = {},now = { now },
        )
        fun respond(value: JSONObject, status: Int = 200) { requests.removeAt(0).reply(NativeHttpResult(status,value.toString(),0)) }
        fun offer() { flow.preview(auth()) { _,error -> assertNull(error) };respond(preview()) }
    }
    @Test fun providerStopsBeforeFinishAndOnlyExactReceiptCompletes() {
        val f=Fixture();f.offer();var result: Map<String,Any?>?=null
        f.flow.finish(auth()) { value,error -> assertNull(error);result=value }
        assertTrue(f.trace.indexOf("persist") < f.trace.indexOf("provider-off"))
        assertTrue(f.trace.indexOf("provider-off") < f.trace.indexOf("prepare-stop"))
        assertTrue(f.trace.indexOf("prepare-stop") < f.trace.indexOfFirst { it.endsWith("/finish") })
        assertTrue(f.flow.holdStop);assertTrue(f.requests.single().path.endsWith("/finish"));assertNotNull(f.requests.single().key)
        f.respond(confirmed(false));assertFalse(f.flow.holdStop);assertNull(result)
        f.now=2001;f.flow.tick();assertTrue(f.requests.single().path.endsWith("/receipt"));assertNull(f.requests.single().key)
        f.respond(receipt(true));assertEquals(true,result?.get("providerStopped"));assertNull(f.saved)
        assertTrue(f.trace.contains("clear-exact-ack"))
    }
    @Test fun failedFirstFinishReleasesCleanupAndRestoresExplicitRetryWithOriginalKey() {
        val f=Fixture();f.offer();var error: String?=null
        f.flow.finish(auth()) { _,code -> error=code };val key=f.requests.single().key
        f.respond(JSONObject(),503);assertNotNull(error);assertFalse(f.flow.holdStop);assertNotNull(f.saved)
        val restored=Fixture(f.saved);restored.off=true
        assertTrue(restored.requests.isEmpty());assertFalse(restored.flow.pending().toString().contains("intentToken"))
        restored.flow.retry(JSONObject().put("intentId",id).put("expectedVersion",7),false) { _,_-> }
        assertTrue(restored.requests.single().path.endsWith("/receipt"));restored.respond(JSONObject(),404)
        restored.flow.tick();assertTrue(restored.requests.single().path.endsWith("/finish"));assertEquals(key,restored.requests.single().key)
    }
    @Test fun finishPurposeAndReceiptBindingsRejectOtherOperations() {
        val raw="vanridesdriver://run/finish#v1.$id.7.${"A".repeat(43)}"
        assertTrue(DriverSessionProtocol.isFinishLink(raw));assertFalse(DriverSessionProtocol.isRunLink(raw))
        assertTrue(DriverSessionProtocol.isFinishLink(raw.replace("vanridesdriver://run/finish","https://driver.example/motorista/fim"),"driver.example"))
        assertFalse(DriverSessionProtocol.isFinishLink(raw.replace(".7.",".07.")))
        try { DriverFinishContract.receipt(receipt(true).put("sessionGeneration",3),preview());fail("foreign generation") } catch (_: IllegalArgumentException) { }
    }
    @Test fun recreatedProcessBuildsExactStopWithoutHistoricalOwnerOrFlutterPolling() {
        val f=Fixture(ownerPresent=false);f.offer();var completed=false
        assertNull(f.currentOwner);assertNull(f.pendingStop)
        f.flow.finish(auth()) { value,error -> assertNull(error);completed=value != null }
        assertTrue(f.off);f.respond(confirmed(false));assertFalse(completed)
        val packet = f.pendingStop!!
        val wire = ProductionTrackingWire().request("/driver-app/v1/tracking-sessions/$session/state",packet)
        val stop = JSONObject(wire.first!!)
        assertEquals(session,stop.getString("sessionId"));assertEquals(2,stop.getInt("generation"))
        assertEquals(ref,stop.getString("stopRequestRef"));assertEquals("STOPPED",stop.getString("state"))
        assertEquals(session,wire.second)
        f.now=2001;f.flow.tick();f.respond(receipt(true))
        assertTrue(completed);assertNull(f.pendingStop);assertNull(f.saved)
        try { DriverFinishContract.stoppedPacket(SessionOwner(session,2),ref,SessionOwner(run,3),true,1);fail("foreign owner") } catch (_: IllegalArgumentException) { }
        try { DriverFinishContract.stoppedPacket(SessionOwner(session,2),ref,null,false,1);fail("provider still present") } catch (_: IllegalArgumentException) { }
    }
    @Test fun ownerlessTimeoutAlreadyHasDurableStopForNativeCleanup() {
        val f=Fixture(ownerPresent=false);f.offer();var error: String?=null
        f.flow.finish(auth()) { _,value -> error=value }
        assertTrue(f.off);assertTrue(f.flow.holdStop);assertNotNull(f.durableStop)
        val before = f.durableStop
        f.respond(JSONObject(),503)
        assertNotNull(error);assertFalse(f.flow.holdStop);assertEquals(before,f.durableStop);assertNotNull(f.saved)
        val ownerAndPacket = TrackingStopJournal.read(TrackingStopJournal.record(SessionOwner(session,2),f.durableStop!!))
        val cleanup = ProductionTrackingWire().request("/driver-app/v1/tracking-sessions/$session/state",ownerAndPacket.second)
        val body=JSONObject(cleanup.first!!)
        assertEquals(session,body.getString("sessionId"));assertEquals(2,body.getInt("generation"));assertEquals("STOPPED",body.getString("state"))
        assertTrue(f.requests.isEmpty())
        val baseline=Fixture(ownerPresent=false);baseline.offer();var completed=false
        baseline.flow.finish(auth()) { value,_ -> completed=value != null };baseline.respond(confirmed(true))
        assertTrue(completed);assertNull(baseline.durableStop);assertFalse(baseline.trace.contains("bind-stop"))
    }
}
