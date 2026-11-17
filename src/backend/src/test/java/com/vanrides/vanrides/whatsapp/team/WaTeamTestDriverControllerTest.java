package com.vanrides.vanrides.whatsapp.team;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaTeamTestDriverControllerTest {
    private final WaTeamGitHub github = mock(WaTeamGitHub.class);
    private final WaTeamSessions sessions = new WaTeamSessions(github,
        new WaClock(Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC)));
    private final WaTeamTestDriverOperations operations = mock(WaTeamTestDriverOperations.class);
    private final WaTeamController controller = new WaTeamController(sessions, null, null, operations);
    private final UUID link = UUID.randomUUID();
    private final WaTeamSessions.Session session;
    private final String cookie;

    WaTeamTestDriverControllerTest() {
        String[] state = new String[1];
        when(github.login(anyString(), anyString())).thenAnswer(call -> {
            state[0] = call.getArgument(0);
            return "https://github.com/login/oauth/authorize";
        });
        var token = new WaTeamGitHub.Token("synthetic-token", 300);
        when(github.exchange(eq("synthetic-code"), anyString())).thenReturn(token);
        when(github.actor(token)).thenReturn(new WaTeamGitHub.Actor(999, "synthetic-team"));
        var login = sessions.begin(null);
        cookie = sessions.finish(login.cookie, state[0], "synthetic-code", null);
        session = sessions.require(cookie, null, false);
    }

    @Test
    void legacyDecisionsAndCurrentRejectCloseBodiesReachDomainWithoutPickup() {
        for (var action : WaTeamTestDriverOperations.Action.values()) {
            ObjectNode data = body(action);
            ObjectNode receipt = WaJson.MAPPER.createObjectNode().put("accepted", true);
            when(operations.replay(eq(session.actor), argThat(value -> value != null && value.action() == action && value.pickup() == null)))
                .thenReturn(receipt);
            assertThat(controller.testDriverOperation(request(data))).isSameAs(receipt);
            verify(operations).replay(eq(session.actor), argThat(value -> value != null && value.action() == action && value.pickup() == null
                && (action.decision == null ? value.link() == null : link.equals(value.link()))));
        }
    }

    @Test
    void explicitPointReachesDomainAndAmbiguityKeepsItsDomainError() {
        UUID pickup = UUID.randomUUID();
        ObjectNode data = body(WaTeamTestDriverOperations.Action.APPROVE_TEST_POINT).put("pickupId", pickup.toString());
        when(operations.execute(eq(session.actor), any(), any(), any())).thenThrow(
            new WaTeamProblem(WaTeamProblem.Code.POINT_SELECTION_REQUIRED));
        assertThatThrownBy(() -> controller.testDriverOperation(request(data)))
            .isInstanceOfSatisfying(WaTeamProblem.class,
                problem -> assertThat(problem.code).isEqualTo(WaTeamProblem.Code.POINT_SELECTION_REQUIRED));
        verify(operations).execute(eq(session.actor), argThat(value -> pickup.equals(value.pickup()) && link.equals(value.link())), any(), any());
    }

    @Test
    void nullMalformedUnexpectedFieldsAndInvitePointAreRejectedBeforeDomain() {
        ObjectNode nullPoint = body(WaTeamTestDriverOperations.Action.APPROVE_TEST_POINT);
        nullPoint.putNull("pickupId");
        for (ObjectNode data : new ObjectNode[] {
            nullPoint,
            body(WaTeamTestDriverOperations.Action.APPROVE_TEST_POINT).put("pickupId", "invalid"),
            body(WaTeamTestDriverOperations.Action.APPROVE_TEST_POINT).put("extra", true),
            body(WaTeamTestDriverOperations.Action.ISSUE_TEST_PASSENGER_INVITE).put("pickupId", UUID.randomUUID().toString())
        }) {
            assertThatThrownBy(() -> controller.testDriverOperation(request(data))).isInstanceOf(WaProblem.class);
        }
        verifyNoInteractions(operations);
    }

    private ObjectNode body(WaTeamTestDriverOperations.Action action) {
        ObjectNode result = WaJson.MAPPER.createObjectNode().put("action", action.name())
            .put("operationId", UUID.randomUUID().toString());
        if (action.decision != null) result.put("linkId", link.toString());
        return result;
    }

    private MockHttpServletRequest request(ObjectNode data) {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("__Host-vanrides-team", cookie));
        request.addHeader("X-CSRF-Token", session.csrf);
        request.addHeader("X-Correlation-ID", UUID.randomUUID().toString());
        request.setContent(WaJson.write(data).getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
