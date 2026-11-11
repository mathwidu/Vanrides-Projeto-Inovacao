package com.vanrides.vanrides.whatsapp.team;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaHttp;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "VANRIDES_WA_TEAM_WEB_ENABLED", havingValue = "true")
public final class WaTeamController {
    private static final String LOGIN_COOKIE = "__Host-vanrides-team-login";
    private static final String SESSION_COOKIE = "__Host-vanrides-team";
    private static final String CSRF_HEADER = "X-CSRF-Token";
    private final WaTeamSessions sessions;
    private final WaTeamEvolution evolution;
    private final WaTeamOperations operations;
    private final WaTeamTestDriverOperations testDriver;

    WaTeamController(WaTeamSessions sessions, WaTeamEvolution evolution, WaTeamOperations operations,
                     WaTeamTestDriverOperations testDriver) {
        this.sessions = sessions;
        this.evolution = evolution;
        this.operations = operations;
        this.testDriver = testDriver;
    }

    @GetMapping(value = {"/equipe", "/equipe/"}, produces = "text/html")
    public Resource index() { return new ClassPathResource("static/equipe/index.html"); }

    @GetMapping("/equipe/login")
    public ResponseEntity<Void> login(HttpServletRequest request, HttpServletResponse response) {
        var login = sessions.begin(cookie(request, LOGIN_COOKIE));
        setCookie(response, LOGIN_COOKIE, login.cookie, WaTeamSessions.LOGIN_TTL);
        return redirect(login.redirect);
    }

    @GetMapping("/equipe/callback")
    public ResponseEntity<Void> callback(HttpServletRequest request, HttpServletResponse response) {
        try {
            String state = parameter(request, "state");
            String code = parameter(request, "code");
            if (request.getParameter("error") != null) code = null;
            String session = sessions.finish(cookie(request, LOGIN_COOKIE), state, code, cookie(request, SESSION_COOKIE));
            setCookie(response, SESSION_COOKIE, session, WaTeamSessions.SESSION_TTL);
            return redirect("/equipe");
        } catch (WaProblem failure) {
            String outcome = switch (failure.code()) {
                case ACTION_NOT_ALLOWED -> "denied";
                case DEPENDENCY_UNAVAILABLE, RATE_LIMITED -> "unavailable";
                default -> "invalid";
            };
            return redirect("/equipe?auth=" + outcome);
        } finally {
            setCookie(response, LOGIN_COOKIE, "", 0);
        }
    }

    @GetMapping("/equipe/api/session")
    public Map<String, String> session(HttpServletRequest request) {
        var current = sessions.require(cookie(request, SESSION_COOKIE), null, false);
        return Map.of("login", current.actor.login(), "csrfToken", current.csrf);
    }

    @PostMapping("/equipe/api/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        WaJson.fields(body(request));
        sessions.logout(cookie(request, SESSION_COOKIE), WaHttp.header(request, CSRF_HEADER, true));
        setCookie(response, SESSION_COOKIE, "", 0);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/equipe/api/participants/status")
    public ObjectNode status(HttpServletRequest request) {
        String cookie = cookie(request, SESSION_COOKIE);
        var current = sessions.require(cookie, WaHttp.header(request, CSRF_HEADER, true), true);
        ObjectNode data = body(request);
        WaJson.fields(data, "phoneNumber");
        operations.requireReady();
        var contact = evolution.verify(phone(data));
        sessions.assertLive(cookie, current);
        ObjectNode result = operations.status(contact);
        sessions.assertLive(cookie, current);
        return result;
    }

    @PostMapping("/equipe/api/participants")
    public ObjectNode participants(HttpServletRequest request) {
        String cookie = cookie(request, SESSION_COOKIE);
        var current = sessions.require(cookie, WaHttp.header(request, CSRF_HEADER, true), true);
        var operation = operation(body(request));
        ObjectNode result = operations.replay(current.actor, operation);
        if (result != null) {
            sessions.assertLive(cookie, current);
            return result;
        }
        var contact = evolution.verify(operation.phone());
        sessions.assertLive(cookie, current);
        return operations.execute(current.actor, operation, contact, UUID.fromString(WaHttp.correlation(request)),
            () -> sessions.assertLive(cookie, current));
    }

    @PostMapping("/equipe/api/test-driver/status")
    public ObjectNode testDriverStatus(HttpServletRequest request) {
        String cookie = cookie(request, SESSION_COOKIE);
        var current = sessions.require(cookie, WaHttp.header(request, CSRF_HEADER, true), true);
        WaJson.fields(body(request));
        ObjectNode result = testDriver.status();
        sessions.assertLive(cookie, current);
        return result;
    }

    @PostMapping("/equipe/api/test-driver")
    public ObjectNode testDriverOperation(HttpServletRequest request) {
        String cookie = cookie(request, SESSION_COOKIE);
        var current = sessions.require(cookie, WaHttp.header(request, CSRF_HEADER, true), true);
        var operation = testDriverOperation(body(request));
        ObjectNode result = testDriver.replay(current.actor, operation);
        sessions.assertLive(cookie, current);
        if (result != null) return result;
        return testDriver.execute(current.actor, operation, UUID.fromString(WaHttp.correlation(request)),
            () -> sessions.assertLive(cookie, current));
    }

    private static WaTeamTestDriverOperations.Request testDriverOperation(ObjectNode data) {
        UUID id = WaJson.uuid(data, "operationId");
        if (id.version() != 4 || id.variant() != 2) throw WaProblem.invalid();
        try {
            var action = WaTeamTestDriverOperations.Action.valueOf(WaJson.text(data, "action", 1, 32));
            boolean invite = action.decision == null;
            if (invite) WaJson.fields(data, "action", "operationId");
            else WaJson.fields(data, "action", "operationId", "linkId");
            return new WaTeamTestDriverOperations.Request(action, id, invite ? null : WaJson.uuid(data, "linkId"));
        } catch (IllegalArgumentException invalid) { throw WaProblem.invalid(); }
    }

    private static WaTeamOperations.Request operation(ObjectNode data) {
        WaJson.fields(data, "phoneNumber", "action", "operationId");
        UUID id = WaJson.uuid(data, "operationId");
        if (id.version() != 4 || id.variant() != 2) throw WaProblem.invalid();
        try {
            var action = WaTeamOperations.Action.valueOf(WaJson.text(data, "action", 1, 32));
            return new WaTeamOperations.Request(phone(data), action, id);
        } catch (IllegalArgumentException invalid) { throw WaProblem.invalid(); }
    }

    private static String phone(ObjectNode data) {
        String phone = WaJson.text(data, "phoneNumber", 9, 16);
        if (!phone.matches("\\+[1-9][0-9]{7,14}")) throw WaProblem.invalid();
        return phone;
    }

    private static ObjectNode body(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readNBytes(2049);
            if (body.length > 2048) throw WaProblem.invalid();
            return WaJson.object(body);
        } catch (IOException invalid) { throw WaProblem.invalid(); }
    }

    private static String parameter(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null) return null;
        if (values.length != 1) throw WaProblem.unauthorized();
        return values[0];
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        String result = null;
        for (Cookie cookie : cookies) {
            if (!name.equals(cookie.getName())) continue;
            if (result != null) throw WaProblem.unauthorized();
            result = cookie.getValue();
        }
        return result;
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(location)).build();
    }

    private static void setCookie(HttpServletResponse response, String name, String value, int seconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).path("/").httpOnly(true).secure(true)
            .sameSite("Lax").maxAge(Duration.ofSeconds(seconds)).build().toString());
    }

    @ExceptionHandler(WaProblem.class)
    public ResponseEntity<Map<String, String>> problem(WaProblem failure, HttpServletRequest request) {
        return ResponseEntity.status(failure.code().status()).body(WaHttp.error(request, failure));
    }

    @ExceptionHandler(WaTeamProblem.class)
    public ResponseEntity<Map<String, String>> teamProblem(WaTeamProblem failure, HttpServletRequest request) {
        return ResponseEntity.status(failure.code.status).body(Map.of("code", failure.code.name(),
            "correlationId", WaHttp.correlation(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception ignored, HttpServletRequest request) {
        return problem(WaTeamRemote.unavailable(), request);
    }
}
