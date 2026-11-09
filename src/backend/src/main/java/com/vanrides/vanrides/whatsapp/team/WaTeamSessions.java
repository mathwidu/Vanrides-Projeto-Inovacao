package com.vanrides.vanrides.whatsapp.team;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Opaque browser cookies reference bounded, process-local sessions. A restart logs everyone out. */
@Component
final class WaTeamSessions {
    static final int LOGIN_TTL = 300;
    static final int SESSION_TTL = 1800;
    private static final int MAX_ENTRIES = 128;
    private final WaTeamGitHub github;
    private final WaClock clock;
    private final Map<String, Pending> pending = new HashMap<>();
    private final Map<String, Session> sessions = new HashMap<>();

    private record Pending(String state, String verifier, Instant expires) { }
    static final class Login {
        final String cookie;
        final String redirect;
        Login(String cookie, String redirect) { this.cookie = cookie; this.redirect = redirect; }
    }
    static final class Session {
        final WaTeamGitHub.Actor actor;
        final String csrf;
        private final WaTeamGitHub.Token token;
        private final Instant expires;
        Session(WaTeamGitHub.Actor actor, WaTeamGitHub.Token token, Instant expires) {
            this.actor = actor;
            this.token = token;
            this.expires = expires;
            this.csrf = WaCrypto.randomToken();
        }
    }

    WaTeamSessions(WaTeamGitHub github, WaClock clock) {
        this.github = github;
        this.clock = clock;
    }

    synchronized Login begin(String previous) {
        Instant now = clock.now();
        pending.values().removeIf(entry -> !entry.expires().isAfter(now));
        if (validToken(previous)) pending.remove(WaCrypto.digest(previous));
        if (pending.size() >= MAX_ENTRIES) throw new WaProblem(WaProblem.Code.RATE_LIMITED);
        String cookie = WaCrypto.randomToken(), state = WaCrypto.randomToken(), verifier = WaCrypto.randomToken();
        pending.put(WaCrypto.digest(cookie), new Pending(state, verifier, now.plusSeconds(LOGIN_TTL)));
        String challenge = WaCrypto.base64(WaCrypto.sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        return new Login(cookie, github.login(state, challenge));
    }

    String finish(String pendingCookie, String state, String code, String previousSession) {
        Pending attempt = consume(pendingCookie, state);
        if (code == null || !code.matches("[A-Za-z0-9_-]{8,256}")) throw WaProblem.unauthorized();
        Instant started = clock.now();
        var token = github.exchange(code, attempt.verifier());
        var actor = github.actor(token);
        github.requireRepositoryWrite(token);
        Instant expiry = started.plusSeconds(Math.min(SESSION_TTL, token.expiresIn()));
        return register(actor, token, expiry, previousSession);
    }

    private synchronized Pending consume(String cookie, String state) {
        if (!validToken(cookie) || !validToken(state)) throw WaProblem.unauthorized();
        String key = WaCrypto.digest(cookie);
        Pending value = pending.get(key);
        if (value == null || !value.expires().isAfter(clock.now()) || !WaCrypto.same(value.state(), state))
            throw WaProblem.unauthorized();
        pending.remove(key);
        return value;
    }

    private synchronized String register(WaTeamGitHub.Actor actor, WaTeamGitHub.Token token, Instant expires, String previous) {
        Instant now = clock.now();
        sessions.values().removeIf(value -> !value.expires.isAfter(now));
        if (!expires.isAfter(now)) throw WaProblem.unauthorized();
        if (validToken(previous)) sessions.remove(WaCrypto.digest(previous));
        if (sessions.size() >= MAX_ENTRIES) throw new WaProblem(WaProblem.Code.RATE_LIMITED);
        String cookie = WaCrypto.randomToken();
        sessions.put(WaCrypto.digest(cookie), new Session(actor, token, expires));
        return cookie;
    }

    Session require(String cookie, String csrf, boolean mutation) {
        Session session = lookup(cookie);
        if (mutation && !WaCrypto.same(session.csrf, csrf)) throw WaProblem.denied();
        try {
            github.requireRepositoryWrite(session.token);
        } catch (WaProblem problem) {
            if (problem.code() == WaProblem.Code.INVALID_CREDENTIAL || problem.code() == WaProblem.Code.ACTION_NOT_ALLOWED)
                remove(cookie, session);
            throw problem;
        }
        assertLive(cookie, session);
        return session;
    }

    void logout(String cookie, String csrf) {
        if (!validToken(cookie)) return;
        Session session;
        try { session = lookup(cookie); }
        catch (WaProblem expired) { return; }
        if (!WaCrypto.same(session.csrf, csrf)) throw WaProblem.denied();
        remove(cookie, session);
    }

    void assertLive(String cookie, Session session) {
        if (lookup(cookie) != session) throw WaProblem.unauthorized();
    }

    private synchronized Session lookup(String cookie) {
        if (!validToken(cookie)) throw WaProblem.unauthorized();
        String key = WaCrypto.digest(cookie);
        Session session = sessions.get(key);
        if (session == null) throw WaProblem.unauthorized();
        if (!session.expires.isAfter(clock.now())) {
            sessions.remove(key);
            throw WaProblem.unauthorized();
        }
        return session;
    }

    private synchronized void remove(String cookie, Session session) {
        sessions.remove(WaCrypto.digest(cookie), session);
    }

    private static boolean validToken(String value) { return value != null && value.matches("[A-Za-z0-9_-]{43}"); }
}
