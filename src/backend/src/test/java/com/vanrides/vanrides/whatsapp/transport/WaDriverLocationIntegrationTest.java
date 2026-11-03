package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaDriverLocationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_location_130");
    private WaTrackingTestSupport t;
    @BeforeEach void setup() { t=new WaTrackingTestSupport(POSTGRES); t.activate(); sample(1,0); }
    private void sample(long seq,double longitude) { t.tracking.samples(t.actor,t.session(),t.batch(t.sample(seq,0,5).put("longitude",longitude))); }
    private ObjectNode query() {
        return query(t.f.passenger);
    }
    private ObjectNode query(UUID actor) {
        var lease=t.f.s.text(actor,"onde está?");
        var context=t.f.s.commands.context(t.f.s.worker,lease.id(),lease.token());
        assertTrue(context.path("allowedCommands").toString().contains("GET_DRIVER_LOCATION"));
        assertFalse(context.toString().contains("latitude"));
        var result=t.f.s.execute(lease,"GET_DRIVER_LOCATION",WaJson.object("{}")).requireSuccess();
        t.f.s.finish(lease); return result;
    }
    private UUID box(ObjectNode result) { return UUID.fromString(result.path("responseIds").get(0).asText()); }
    private ObjectNode reserve(UUID box) { return t.f.s.authorize(WaRunGpsDeliveryTestSupport.lease(t,box)); }
    @Test void explicitQueryReturnsOnlyReferencesAndAuthorizesStaticVehiclePin() {
        var result=query(); var reservation=reserve(box(result));
        assertEquals("STATIC_PIN",reservation.path("content").path("type").asText());
        assertEquals("Localização atual do veículo",reservation.path("content").path("label").asText());
        assertEquals(0,reservation.path("content").path("latitude").asDouble());
        assertFalse(result.toString().contains("latitude"));
        Instant captured=Instant.parse(reservation.path("content").path("capturedAt").asText());
        assertFalse(Instant.parse(reservation.path("submitBefore").asText()).isAfter(captured.plusSeconds(20)));
        assertFalse(t.f.s.string("SELECT payload_json::text FROM wa.message_outbox WHERE id=?",box(result)).contains("latitude"));
        WaRunGpsDeliveryTestSupport.submitted(t,reservation,"synthetic-static-130");
        assertFalse(t.f.s.string("SELECT result_json::text FROM wa.message_attempt WHERE outbox_id=?",box(result)).contains("latitude"));
    }
    @Test void commandReplayKeepsOneResponseAndDoesNotCreateAnotherPin() {
        var lease=t.f.s.text(t.f.passenger,"localização"); var body=t.f.s.body(lease,"GET_DRIVER_LOCATION",WaJson.object("{}"));
        var first=t.f.s.execute(lease,body,"synthetic-location-key").requireSuccess();
        assertEquals(first,t.f.s.execute(lease,body,"synthetic-location-replay-key").requireSuccess());
        assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE location_requested"));
    }
    @Test void successiveQueriesUseDistinctCommandsAndLatestAcquisitions() {
        var first=query(); var r1=reserve(box(first));
        WaRunGpsDeliveryTestSupport.submitted(t,r1,"synthetic-location-first");
        t.h.time.advance(2); sample(2,.001);
        var second=query(); var r2=reserve(box(second));
        assertNotEquals(box(first),box(second));
        assertNotEquals(r1.path("content").path("capturedAt"),r2.path("content").path("capturedAt"));
        assertEquals(.001,r2.path("content").path("longitude").asDouble());
    }
    @Test void delayedQueueUsesNewAcquisitionInsteadOfCapturedAtOfTheCommand() {
        var result=query(); var item=WaRunGpsDeliveryTestSupport.lease(t,box(result));
        t.h.time.advance(21); sample(2,.002);
        var reservation=t.f.s.authorize(item);
        assertEquals("STATIC_PIN",reservation.path("content").path("type").asText());
        assertEquals(.002,reservation.path("content").path("longitude").asDouble());
        assertEquals(t.h.clock.now().toString(),reservation.path("content").path("capturedAt").asText());
    }
    @Test void delayedQueueWithoutNewAcquisitionReturnsUnavailableText() {
        var result=query(); var item=WaRunGpsDeliveryTestSupport.lease(t,box(result));
        t.h.time.advance(21); var reservation=t.f.s.authorize(item);
        assertEquals("TEXT",reservation.path("content").path("type").asText());
        assertFalse(reservation.toString().contains("latitude"));
    }
    @Test void completedRunNeverReleasesAQueuedPinAndNewQueryIsUnavailable() {
        var first=query(); var item=WaRunGpsDeliveryTestSupport.lease(t,box(first));
        t.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED' WHERE id=?",t.run());
        var response=t.f.s.authorize(item); assertEquals("TEXT",response.path("content").path("type").asText());
        WaRunGpsDeliveryTestSupport.submitted(t,response,"synthetic-ended-location");
        assertEquals("TEXT",reserve(box(query())).path("content").path("type").asText());
    }
    @Test void closedRelationshipInvalidatesAuthorizationBeforeSubmission() {
        var first=query(); var item=WaRunGpsDeliveryTestSupport.lease(t,box(first));
        t.h.jdbc.update("UPDATE wa.driver_passenger_link SET state='CLOSED',valid_until=? WHERE id=?",java.sql.Timestamp.from(t.h.clock.now()),t.f.item.link());
        assertEquals("TEXT",t.f.s.authorize(item).path("content").path("type").asText());
    }
    @Test void revokedDeviceCannotRevealLastStoredPosition() {
        var first=query(); var item=WaRunGpsDeliveryTestSupport.lease(t,box(first));
        t.h.jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=? WHERE id=?",java.sql.Timestamp.from(t.h.clock.now()),t.device);
        assertEquals("TEXT",t.f.s.authorize(item).path("content").path("type").asText());
    }
    @Test void foreignDriverAndUnlinkedPassengerCannotSelectAnArbitraryRun() {
        UUID outsider=t.f.s.actor();t.f.s.driver(outsider);
        var first=reserve(box(query(outsider)));assertEquals("TEXT",first.path("content").path("type").asText());
        WaRunGpsDeliveryTestSupport.submitted(t,first,"synthetic-outsider-unavailable");
        UUID stranger=t.f.attendance.passenger(); var incoming=t.f.s.text(stranger,"onde está?");
        var result=t.f.s.execute(incoming,"GET_DRIVER_LOCATION",WaJson.object("{}")).requireSuccess();t.f.s.finish(incoming);
        assertEquals("TEXT",reserve(box(result)).path("content").path("type").asText());
    }
    @Test void bodyCannotOverrideServerRunOrRecipientAndDegradedGpsIsUnavailable() {
        var lease=t.f.s.text(t.f.passenger,"onde está?");
        assertEquals(WaProblem.Code.INVALID_PAYLOAD,assertThrows(WaProblem.class,()->t.f.s.execute(lease,"GET_DRIVER_LOCATION",WaJson.object("{}").put("runId",t.run().toString()))).code());
        t.f.s.finish(lease); var result=query(); t.h.jdbc.update("UPDATE wa.operation_run SET state='DEGRADED' WHERE id=?",t.run());
        assertEquals("TEXT",reserve(box(result)).path("content").path("type").asText());
    }
    @Test void aStaleMenuSelectionCannotSelectAnotherRunOrChangeTheProfileName() {
        var draft=WaJson.object("{}").put("page",0).put("expiresAt",t.h.clock.now().plusSeconds(300).toString());draft.putArray("runs").add(UUID.randomUUID().toString());
        var context=WaJson.object("{}").put("conversationOwner","LOCATION");context.set("locationDraft",draft);
        t.h.jdbc.update("INSERT INTO wa.conversation_state(identity_id,stage,context_json) VALUES (?,'READY',?::jsonb) ON CONFLICT(identity_id) DO UPDATE SET context_json=EXCLUDED.context_json",t.f.passenger,context.toString());
        var lease=t.f.s.text(t.f.passenger,"1");var result=t.f.s.execute(lease,"GET_DRIVER_LOCATION",WaJson.object("{}")).requireSuccess();t.f.s.finish(lease);
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE location_requested"));
        assertTrue(t.f.s.string("SELECT payload_json->>'text' FROM wa.message_outbox WHERE id=?",box(result)).contains("Escolha"));
        assertNotEquals("1",t.f.s.string("SELECT display_name FROM wa.profile WHERE identity_id=?",t.f.passenger));
    }
    @Test void driverOwnerReceivesOnlyItsOwnVehiclePinWithoutModelGps() {
        var result=query(t.f.driver);var authorized=reserve(box(result));
        assertEquals("STATIC_PIN",authorized.path("content").path("type").asText());
        assertEquals(t.run(),t.h.jdbc.queryForObject("SELECT run_id FROM wa.message_outbox WHERE id=?",UUID.class,box(result)));
        assertFalse(result.toString().contains("latitude"));
    }
    @Test void driverDisabledAfterClaimCannotRevealGpsAndIsNotOfferedAnotherQuery() {
        var result=query(t.f.driver);var item=WaRunGpsDeliveryTestSupport.lease(t,box(result));
        t.h.jdbc.update("UPDATE wa.profile SET driver_enabled=false WHERE identity_id=?",t.f.driver);
        assertEquals("TEXT",t.f.s.authorize(item).path("content").path("type").asText());
        var lease=t.f.s.text(t.f.driver,"onde está?");assertFalse(t.f.s.commands.context(t.f.s.worker,lease.id(),lease.token()).path("allowedCommands").toString().contains("GET_DRIVER_LOCATION"));
    }
    @Test void pausedOwnerAndRevokedRefreshCredentialCannotRevealStoredGps() {
        var result=query(t.f.driver);var item=WaRunGpsDeliveryTestSupport.lease(t,box(result));
        t.h.jdbc.update("UPDATE wa.conversation_state SET stage='PAUSED' WHERE identity_id=?",t.f.driver);
        var response=t.f.s.outbox.authorize(t.f.s.delivery,box(result),WaJson.object("{}").put("leaseToken",item.path("leaseToken").asText()));
        assertTrue(response.error()!=null || response.body().path("content").path("type").asText().equals("TEXT"));
        assertTrue(response.body()==null || !response.body().toString().contains("latitude"));
        t.h.jdbc.update("UPDATE wa.conversation_state SET stage='READY' WHERE identity_id=?",t.f.driver);
        t.h.jdbc.update("UPDATE wa.device_token SET revoked_at=? WHERE device_id=? AND kind='REFRESH'",java.sql.Timestamp.from(t.h.clock.now()),t.device);
        assertNull(new com.vanrides.vanrides.whatsapp.tracking.WaVehicleLocation(t.h.jdbc,t.h.clock,t.h.settings).pin(t.f.driver,t.run()));
    }
    private UUID menu(ObjectNode result,String external) {
        UUID id=box(result);t.h.jdbc.update("UPDATE wa.message_outbox SET location_requested=false,purpose='RESPONSE' WHERE id=?",id);
        WaRunGpsDeliveryTestSupport.submitted(t,reserve(id),external);
        UUID source=t.h.jdbc.queryForObject("SELECT r.inbox_event_id FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id WHERE o.id=?",UUID.class,id);
        var draft=WaJson.object("{}").put("page",0).put("expiresAt",t.h.clock.now().plusSeconds(300).toString());draft.putArray("runs").add(t.run().toString());
        var context=WaJson.object("{}").put("conversationOwner","LOCATION");context.set("locationDraft",draft);context.putObject("ownerPromptEvents").put("LOCATION",source.toString());
        t.h.jdbc.update("UPDATE wa.conversation_state SET context_json=?::jsonb,last_event_id=? WHERE identity_id=?",context.toString(),source,t.f.passenger);return id;
    }
    private ObjectNode quotedSelection(String external) {
        var message=WaJson.object("{}");message.putObject("extendedTextMessage").put("text","1").putObject("contextInfo").put("stanzaId",external);
        var lease=t.f.s.claim(t.f.s.pending(t.f.passenger,message,t.h.clock.now()));
        var result=t.f.s.execute(lease,"GET_DRIVER_LOCATION",WaJson.object("{}")).requireSuccess();t.f.s.finish(lease);return result;
    }
    @Test void quotedOldMenuCannotResolveTheNewLocationDraft() {
        menu(query(),"synthetic-menu-old");t.h.time.advance(2);menu(query(),"synthetic-menu-current");
        var result=quotedSelection("synthetic-menu-old");
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE id=? AND location_requested",box(result)));
        assertTrue(t.f.s.string("SELECT payload_json->>'text' FROM wa.message_outbox WHERE id=?",box(result)).contains("anterior"));
    }
    @Test void quotedCurrentMenuUsesOnlyItsOwnPersistedSelection() {
        menu(query(),"synthetic-menu-current");var result=quotedSelection("synthetic-menu-current");
        assertEquals("STATIC_PIN",reserve(box(result)).path("content").path("type").asText());
    }
    @Test void aNewQueryAfterTrackingLeaseExpiryStillSendsUnavailableText() {
        t.h.time.advance(301);t.watchdog.tick();t.h.connection(t.f.s.channel,"open",t.h.clock.now());
        assertEquals("DEGRADED",t.runState());assertEquals("EXPIRED",t.sessionState());
        var result=query();var response=reserve(box(result));
        assertEquals("TEXT",response.path("content").path("type").asText());assertFalse(response.toString().contains("latitude"));
    }
}
