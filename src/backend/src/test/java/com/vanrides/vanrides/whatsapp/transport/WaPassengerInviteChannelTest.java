package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.transport.WaPassengerInviteChannel.Permission;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class WaPassengerInviteChannelTest {
    private static final Instant NOW = Instant.parse("2030-06-01T12:00:00Z");

    @Test
    void expandsBrazilianMobileFormsWhilePreservingTheExactNumber() {
        assertEquals(List.of("5511987654321", "551187654321"),
            WaPassengerInviteChannel.phoneVariants("5511987654321"));
        assertEquals(List.of("551187654321", "5511987654321"),
            WaPassengerInviteChannel.phoneVariants("551187654321"));
        assertEquals(List.of("551123456789", "551123456789"),
            WaPassengerInviteChannel.phoneVariants("551123456789"));
        assertEquals(List.of("79990001234", "79990001234"),
            WaPassengerInviteChannel.phoneVariants("79990001234"));
    }

    @Test
    void consentUsesTheOlderOfOriginalAndReceivedTime() {
        Instant received = NOW.minusSeconds(60);
        Instant occurred = received.minusSeconds(120);
        var optIn = new Permission(true, true, received, occurred);
        Instant expiry = occurred.plusSeconds(86_400);
        assertEquals(expiry, WaPassengerInviteChannel.permissionDeadline(List.of(optIn), NOW));
        assertNull(WaPassengerInviteChannel.permissionDeadline(List.of(optIn), expiry));
    }

    @Test
    void optInRequiresOriginalTimeWithinTheReceiptWindow() {
        assertNull(WaPassengerInviteChannel.permissionDeadline(List.of(new Permission(true, true, NOW, null)), NOW));
        assertNull(WaPassengerInviteChannel.permissionDeadline(List.of(
            new Permission(true, true, NOW, NOW.minusSeconds(301))), NOW));
        assertNull(WaPassengerInviteChannel.permissionDeadline(List.of(
            new Permission(true, true, NOW, NOW.plusSeconds(31))), NOW));
        assertEquals(NOW.minusSeconds(300).plusSeconds(86_400), WaPassengerInviteChannel.permissionDeadline(List.of(
            new Permission(true, true, NOW, NOW.minusSeconds(300))), NOW));
        assertEquals(NOW.plusSeconds(86_400), WaPassengerInviteChannel.permissionDeadline(List.of(
            new Permission(true, true, NOW, NOW.plusSeconds(30))), NOW));
    }

    @Test
    void forgedSenderAndDelayedOptInCannotOverrideAnOptOut() {
        Instant earlier = NOW.minusSeconds(20), later = NOW.minusSeconds(10);
        var unknownSender = new Permission(true, false, NOW, NOW);
        assertNull(WaPassengerInviteChannel.permissionDeadline(List.of(unknownSender), NOW));
        var optOut = new Permission(false, true, later, later);
        var delayedOptIn = new Permission(true, true, NOW, earlier);
        assertNull(WaPassengerInviteChannel.permissionDeadline(List.of(delayedOptIn, optOut), NOW));
        var renewedOptIn = new Permission(true, true, NOW, NOW);
        assertEquals(NOW.plusSeconds(86_400),
            WaPassengerInviteChannel.permissionDeadline(List.of(renewedOptIn, optOut), NOW));
        assertNull(WaPassengerInviteChannel.permissionDeadline(List.of(optOut, delayedOptIn), NOW));
        var optOutWithoutTime = new Permission(false, true, later, null);
        assertNull(WaPassengerInviteChannel.permissionDeadline(List.of(optOutWithoutTime), NOW));
        assertNull(WaPassengerInviteChannel.permissionDeadline(List.of(delayedOptIn, optOutWithoutTime), NOW));
        assertEquals(NOW.plusSeconds(86_400),
            WaPassengerInviteChannel.permissionDeadline(List.of(renewedOptIn, optOutWithoutTime), NOW));
    }

    @Test
    void queuesOnlyGenericPayloadAndBindsTheCiphertextToThePassenger() {
        UUID box = UUID.randomUUID(), command = UUID.randomUUID();
        UUID passenger = UUID.randomUUID(), invite = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var crypto = new WaCrypto(WaTransportTestSupport.settings(true, true));
        var privateDelivery = new WaPrivateInviteDelivery(jdbc, new WaClock(Clock.fixed(NOW, ZoneOffset.UTC)), crypto);
        String text = "PASSAGEIRO " + WaCrypto.randomToken();
        privateDelivery.queuePassenger(box, command, passenger, invite, text, NOW.plusSeconds(30));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), args.capture());
        Object[] values = args.getValue();
        assertEquals(passenger, values[1]);
        assertEquals("driver-invite-passenger", values[3]);
        assertFalse(values[4].toString().contains("PASSAGEIRO"));
        assertEquals(Timestamp.from(NOW.plusSeconds(30)), values[6]);
        byte[] cipher = (byte[]) values[9];
        assertEquals(text, crypto.decrypt(WaPrivateInviteDelivery.aad(box, command, passenger), cipher));
        assertThrows(RuntimeException.class,
            () -> crypto.decrypt(WaPrivateInviteDelivery.aad(box, command, UUID.randomUUID()), cipher));
    }
}
