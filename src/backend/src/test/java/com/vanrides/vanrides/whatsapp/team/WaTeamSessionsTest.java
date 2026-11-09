package com.vanrides.vanrides.whatsapp.team;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaTeamSessionsTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String CODE = "synthetic-oauth-code";
    private final WaTeamGitHub github = mock(WaTeamGitHub.class);
    private final Clock time = mock(Clock.class);
    private final WaTeamSessions sessions = new WaTeamSessions(github, new WaClock(time));
    private final WaTeamGitHub.Token token = new WaTeamGitHub.Token("synthetic-oauth-token", 28800);
    private final WaTeamGitHub.Actor actor = new WaTeamGitHub.Actor(999, "synthetic-team");
    private String state;
    private String challenge;

    private WaTeamSessions.Login begin() {
        when(time.instant()).thenReturn(NOW);
        when(github.login(anyString(), anyString())).thenAnswer(invocation -> {
            state = invocation.getArgument(0);
            challenge = invocation.getArgument(1);
            return "https://github.com/login/oauth/authorize";
        });
        when(github.exchange(eq(CODE), anyString())).thenReturn(token);
        when(github.actor(token)).thenReturn(actor);
        return sessions.begin(null);
    }

    @Test
    void stateCookieAndVerifierAreBoundAndCallbackIsSingleUse() {
        var login = begin();
        assertThat(login.cookie).hasSize(43).isNotEqualTo(state);
        assertThatThrownBy(() -> sessions.finish(login.cookie, WaCrypto.randomToken(), CODE, null)).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> sessions.finish(WaCrypto.randomToken(), state, CODE, null)).isInstanceOf(WaProblem.class);
        verify(github, never()).exchange(anyString(), anyString());
        String cookie = sessions.finish(login.cookie, state, CODE, null);
        var verifier = ArgumentCaptor.forClass(String.class);
        verify(github).exchange(eq(CODE), verifier.capture());
        assertThat(challenge).isEqualTo(WaCrypto.base64(WaCrypto.sha256(verifier.getValue().getBytes(StandardCharsets.US_ASCII))));
        assertThat(cookie).isNotEqualTo(login.cookie);
        assertThatThrownBy(() -> sessions.finish(login.cookie, state, CODE, null)).isInstanceOf(WaProblem.class);
        verify(github, times(1)).exchange(anyString(), anyString());
    }

    @Test
    void callbackExpiryAndRepositoryDenialPreventSessionCreation() {
        var expired = begin();
        when(time.instant()).thenReturn(NOW.plusSeconds(WaTeamSessions.LOGIN_TTL));
        assertThatThrownBy(() -> sessions.finish(expired.cookie, state, CODE, null)).isInstanceOf(WaProblem.class);
        verify(github, never()).exchange(anyString(), anyString());

        var denied = begin();
        doThrow(WaProblem.denied()).when(github).requireRepositoryWrite(token);
        assertThatThrownBy(() -> sessions.finish(denied.cookie, state, CODE, null))
            .isInstanceOfSatisfying(WaProblem.class, e -> assertThat(e.code()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED));
        assertThatThrownBy(() -> sessions.finish(denied.cookie, state, CODE, null))
            .isInstanceOfSatisfying(WaProblem.class, e -> assertThat(e.code()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL));
    }

    @Test
    void csrfLogoutExpiryAndRepositoryRevocationAreEnforcedServerSide() {
        var login = begin();
        String cookie = sessions.finish(login.cookie, state, CODE, null);
        var session = sessions.require(cookie, null, false);
        assertThat(session.actor).isEqualTo(actor);
        assertThatThrownBy(() -> sessions.require(cookie, "invalid", true)).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> sessions.logout(cookie, "invalid")).isInstanceOf(WaProblem.class);
        assertThat(sessions.require(cookie, session.csrf, true)).isSameAs(session);
        verify(github, times(3)).requireRepositoryWrite(token);
        sessions.logout(cookie, session.csrf);
        assertThatThrownBy(() -> sessions.assertLive(cookie, session)).isInstanceOf(WaProblem.class);

        var relogin = begin();
        String newCookie = sessions.finish(relogin.cookie, state, CODE, null);
        doThrow(WaProblem.denied()).doNothing().when(github).requireRepositoryWrite(token);
        assertThatThrownBy(() -> sessions.require(newCookie, null, false)).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> sessions.require(newCookie, null, false))
            .isInstanceOfSatisfying(WaProblem.class, e -> assertThat(e.code()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL));

        var last = begin();
        String lastCookie = sessions.finish(last.cookie, state, CODE, null);
        when(time.instant()).thenReturn(NOW.plusSeconds(WaTeamSessions.SESSION_TTL));
        assertThatThrownBy(() -> sessions.require(lastCookie, null, false)).isInstanceOf(WaProblem.class);
    }
}
