package com.vanrides.vanrides.whatsapp.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.env.MockEnvironment;

/** Exercises the conversation and its existing persistence helper without a PostgreSQL runtime. */
class WaAttendanceEmptySelectionTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final TestJdbc jdbc = new TestJdbc();
    private final WaAttendanceConversation conversation = new WaAttendanceConversation(jdbc,
        new WaClock(Clock.fixed(NOW, ZoneOffset.UTC)),
        new WaSettings(new MockEnvironment().withProperty("vanrides.wa.environment", "test")), null);

    @Test void emptyPassengerCatalogueDoesNotCaptureTheFollowingDriverIntent() {
        var actor = actor("PASSENGER");
        var result = advance(actor, "VOU HOJE");

        assertThat(result.decision().error()).isNull();
        assertThat(result.text()).isEqualTo("Não há nenhuma viagem próxima registrada. Aguarde a chegada do lembrete.");
        assertThat(jdbc.context.has("attendanceDraft")).isFalse();
        assertThat(WaConversationOwnership.owner(jdbc, ID)).isEqualTo("READY");
        assertThat(conversation.offer(actor, event("SOU MOTORISTA"))).isNull();
        assertThat(conversation.help(actor, event("AJUDA"))).isNull();
    }

    @Test void emptyCatalogueRestoresTheSuspendedPassengerWizard() {
        var previous = WaJson.object("{\"stage\":\"PREF_APPROACH\"}");
        jdbc.context.set("passengerDraft", previous.deepCopy());
        jdbc.context.put("conversationOwner", "PASSENGER_SETUP");

        advance(actor("PASSENGER"), "VOU HOJE NA IDA");

        assertThat(jdbc.context.has("attendanceDraft")).isFalse();
        assertThat(jdbc.context.path("passengerDraft")).isEqualTo(previous);
        assertThat(WaConversationOwnership.owner(jdbc, ID)).isEqualTo("PASSENGER_SETUP");
    }

    @Test void thePassengerMenuAndItsNavigationAreGone() {
        jdbc.catalogue = List.of(question(2, "OUTBOUND"), question(3, "RETURN"));
        var actor = actor("PASSENGER");

        for (String text : List.of("PRESENÇA", "PRESENÇA HOJE", "2", "PRÓXIMA", "ANTERIOR", "ABANDONAR"))
            assertThat(conversation.offer(actor, event(text))).as(text).isNull();
        assertThat(jdbc.context.has("attendanceDraft")).isFalse();

        for (String text : List.of("VOU 2", "Talvez vou", "Acho que não vou", "Qual o horário da ida e da volta?", "ida e volta?"))
            assertThat(conversation.offer(actor, event(text))).as(text).isNull();
        assertThat(jdbc.context.has("attendanceDraft")).isFalse();
    }

    @Test void twoChoicesInOneMessageGetTheOneTripAtATimeText() {
        jdbc.catalogue = List.of(question(2, "OUTBOUND"), question(3, "RETURN"));
        var actor = actor("PASSENGER");

        for (String text : List.of("VOU IDA E VOLTA", "NÃO VOU HOJE E AMANHÃ")) {
            assertThat(advance(actor, text).text())
                .isEqualTo("Responda uma viagem por vez: responda ao lembrete da viagem com VOU ou NÃO VOU.");
            assertThat(jdbc.context.has("attendanceDraft")).isFalse();
            assertThat(WaConversationOwnership.owner(jdbc, ID)).isEqualTo("READY");
        }
    }

    @Test void aPassengerOfferThatWentStaleBeforeExecutionGetsTheEndedTripText() {
        var result = conversation.execute(actor("PASSENGER"), event("VOU"), "SET_ATTENDANCE", WaJson.MAPPER.createObjectNode());

        assertThat(result.decision().error()).isNotNull();
        assertThat(result.text()).isEqualTo("Essa viagem já foi encerrada. Não é possível alterar a presença dela.");
    }

    @Test void aBareAnswerOffersTheEarliestTripAndKeepsNoDraft() {
        jdbc.catalogue = List.of(question(2, "OUTBOUND"), question(3, "RETURN"));

        var offer = conversation.offer(actor("PASSENGER"), event("NÃO VOU"));

        assertThat(offer.command()).isEqualTo("SET_ATTENDANCE");
        assertThat(offer.choice()).isEqualTo("NOT_GOING");
        assertThat(offer.target()).isEqualTo(new UUID(0, 2));
        assertThat(jdbc.context.has("attendanceDraft")).isFalse();
    }

    @Test void aTripWhoseStopWasPassedIsSkippedByABareAnswer() {
        jdbc.catalogue = List.of(question(2, "OUTBOUND").put("stopLocked", true), question(3, "RETURN"));

        assertThat(conversation.offer(actor("PASSENGER"), event("VOU")).target()).isEqualTo(new UUID(0, 3));
    }

    @Test void emptyDriverCatalogueAlsoLeavesNoAttendanceSelection() {
        var actor = actor("DRIVER");
        assertThat(advance(actor, "PRESENÇAS").text())
            .contains("Nenhuma ocorrência disponível", "Confirme suas rotinas", "PRESENÇAS")
            .doesNotContain("Aguarde o motorista");
        assertThat(jdbc.context.has("attendanceDraft")).isFalse();
        assertThat(WaConversationOwnership.owner(jdbc, ID)).isEqualTo("READY");
        assertThat(conversation.offer(actor, event("SOU MOTORISTA"))).isNull();
    }

    private WaAttendanceConversation.Result advance(WaProfileState actor, String text) {
        var event = event(text);
        assertThat(conversation.offer(actor, event).command()).isEqualTo("ADVANCE_ATTENDANCE");
        return conversation.execute(actor, event, "ADVANCE_ATTENDANCE", WaJson.MAPPER.createObjectNode());
    }

    private static WaProfileState actor(String role) {
        return new WaProfileState(ID, role, "ACTIVE", "Pessoa sintética", "wa-mvp-v1", role.equals("DRIVER"), 3);
    }

    private static WaInboxAccess.Context event(String text) {
        return new WaInboxAccess.Context(UUID.randomUUID(), ID,
            WaJson.MAPPER.createObjectNode().put("text", text).put("occurredAt", NOW.toString()),
            "TEXT", NOW.plusSeconds(60), NOW.plusSeconds(3600));
    }

    private static ObjectNode question(int number, String direction) {
        return WaJson.MAPPER.createObjectNode().put("id", new UUID(0, number).toString())
            .put("local_date", "2030-01-01").put("direction", direction).put("timezone", "America/Sao_Paulo")
            .put("time", "15:00:00").put("routineName", "Rotina sintética").put("runStarted", true);
    }

    /** Only transports JSON rows and saved parameters; domain decisions use the real helpers. */
    private static final class TestJdbc extends JdbcTemplate {
        private ObjectNode context = WaJson.MAPPER.createObjectNode();
        private List<ObjectNode> catalogue = List.of();

        @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            if (sql.contains("FROM wa.conversation_state")) {
                assertThat(args).containsExactly(ID);
                return mapped(mapper, List.of(context.toString()));
            }
            if (sql.contains("FROM wa.attendance_question q"))
                return mapped(mapper, catalogue.stream().map(ObjectNode::toString).toList());
            if (sql.contains("FROM wa.routine_occurrence o")) return List.of();
            throw new AssertionError("Unexpected test query");
        }

        @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            assertThat(sql).startsWith("SELECT instance_id FROM wa.integration_inbox");
            assertThat(args).hasSize(2).contains(ID);
            return type.cast("synthetic-instance");
        }

        @Override public int update(String sql, Object... args) {
            if (sql.contains("INSERT INTO wa.conversation_state")) {
                assertThat(args[0]).isEqualTo(ID);
                context = WaJson.object((String) args[2]);
            } else assertThat(sql).startsWith("UPDATE wa.profile SET version=version+1");
            return 1;
        }

        private static <T> List<T> mapped(RowMapper<T> mapper, List<String> rows) {
            List<T> result = new ArrayList<>();
            for (String row : rows) {
                ResultSet rs = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getString") && Integer.valueOf(1).equals(args[0])) return row;
                        throw new AssertionError("Unexpected test result accessor");
                    });
                try { result.add(mapper.mapRow(rs, result.size())); }
                catch (SQLException failure) { throw new AssertionError(failure); }
            }
            return result;
        }
    }
}
