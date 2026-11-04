package com.vanrides.vanrides.whatsapp.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class WaPassengerHelpTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final UUID ID = UUID.randomUUID();
    private final TestJdbc jdbc = new TestJdbc();
    private final WaPassengerSetup setup = new WaPassengerSetup(jdbc,
            new WaClock(Clock.fixed(NOW, ZoneOffset.UTC)), null, null);

    @Test
    void readyPassengerWithoutDriverIsToldHowToSendAnInvitation() {
        assertInvitationHelp(setup.help(passenger()));
        assertThat(jdbc.writes).isEmpty();
    }

    @Test
    void continueWithoutDraftOrLinkPreservesRejectionAndExplainsTheNextStep() {
        var event = new WaInboxAccess.Context(UUID.randomUUID(), ID,
                WaJson.MAPPER.createObjectNode().put("text", "CONTINUAR CADASTRO")
                        .put("occurredAt", NOW.toString()),
                "TEXT", NOW.plusSeconds(60), NOW.plusSeconds(3600));
        var result = setup.execute(passenger(), event, "ADVANCE_PASSENGER_SETUP",
                WaJson.MAPPER.createObjectNode(), UUID.randomUUID());

        assertThat(result.decision().error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(result.delivery()).isNull();
        assertInvitationHelp(result.text());
        // Existing recovery may save the empty conversation draft/profile version, never a link.
        assertThat(jdbc.writes).hasSize(2);
        assertThat(jdbc.writes.get(0)).contains("INSERT INTO wa.conversation_state");
        assertThat(jdbc.writes.get(1)).startsWith("UPDATE wa.profile SET version=version+1");
    }

    @Test
    void existingPassengerDraftKeepsItsCurrentQuestion() {
        jdbc.context.set("passengerDraft", WaJson.object("{\"stage\":\"NAME\"}"));
        assertThat(setup.help(passenger())).isEqualTo("Como você prefere ser chamado?");
        assertThat(jdbc.linkReads).isZero();
        assertThat(jdbc.writes).isEmpty();
    }

    @Test
    void requestedOrApprovedLinkDoesNotClaimThePassengerHasNoDriver() {
        for (String state : List.of("REQUESTED", "APPROVED")) {
            jdbc.links = List.of(WaJson.MAPPER.createObjectNode().put("state", state));
            assertThat(setup.help(passenger())).isNull();
        }
        assertThat(jdbc.writes).isEmpty();
    }

    @Test
    void driverAndIncompletePassengerKeepTheirExistingOnboardingHelp() {
        for (var actor : List.of(
                new WaProfileState(ID, "DRIVER", "ACTIVE", "Motorista", WaOnboardingCommands.NOTICE_VERSION, true, 3),
                WaProfileState.absent(ID),
                new WaProfileState(ID, "PASSENGER", "ONBOARDING", "Passageiro", WaOnboardingCommands.NOTICE_VERSION, false, 3),
                new WaProfileState(ID, "PASSENGER", "ACTIVE", null, WaOnboardingCommands.NOTICE_VERSION, false, 3),
                new WaProfileState(ID, "PASSENGER", "ACTIVE", "Passageiro", null, false, 3))) {
            assertThat(setup.help(actor)).isNull();
        }
        assertThat(jdbc.linkReads).isZero();
        assertThat(jdbc.writes).isEmpty();
    }

    private static WaProfileState passenger() {
        return new WaProfileState(ID, "PASSENGER", "ACTIVE", "Passageiro",
                WaOnboardingCommands.NOTICE_VERSION, false, 3);
    }

    private static void assertInvitationHelp(String text) {
        assertThat(text).contains("Peça ao seu motorista um convite de passageiro do VanRides",
                "envie esse convite aqui", "o vínculo depende da aprovação do motorista", "FALAR COM SUPORTE")
                .doesNotContain("próximas etapas", "aplicativo", "PASSAGEIROS", "CORRIGIR PONTO");
    }

    private static final class TestJdbc extends JdbcTemplate {
        private final ObjectNode context = WaJson.MAPPER.createObjectNode();
        private final List<String> writes = new ArrayList<>();
        private List<ObjectNode> links = List.of();
        private int linkReads;

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            assertThat(args).containsExactly(ID);
            if (sql.contains("FROM wa.conversation_state")) return (List<T>) (List<?>) List.of(context);
            if (sql.contains("FROM wa.driver_passenger_link")) {
                linkReads++;
                return (List<T>) (List<?>) links;
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            writes.add(sql);
            return 1;
        }
    }
}
