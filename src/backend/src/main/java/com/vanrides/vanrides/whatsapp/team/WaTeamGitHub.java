package com.vanrides.vanrides.whatsapp.team;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class WaTeamGitHub {
    private static final String REPOSITORY = "mathwidu/Vanrides-Projeto-Inovacao";
    private static final String API = "https://api.github.com";
    private static final String OAUTH = "https://github.com/login/oauth/";
    private final WaTeamSettings settings;
    private final WaTeamRemote remote;

    record Actor(long id, String login) { }

    static final class Token {
        private final String value;
        private final long expiresIn;
        Token(String value, long expiresIn) { this.value = value; this.expiresIn = expiresIn; }
        long expiresIn() { return expiresIn; }
    }

    WaTeamGitHub(WaTeamSettings settings, WaTeamRemote remote) {
        this.settings = settings;
        this.remote = remote;
    }

    String login(String state, String challenge) {
        return OAUTH + "authorize?client_id=" + encode(settings.clientId())
            + "&redirect_uri=" + encode(settings.callback()) + "&state=" + encode(state)
            + "&code_challenge=" + encode(challenge) + "&code_challenge_method=S256&prompt=select_account&allow_signup=false";
    }

    Token exchange(String code, String verifier) {
        var reply = remote.exchange(URI.create(OAUTH + "access_token"),
            Map.of("Content-Type", "application/x-www-form-urlencoded"),
            "client_id=" + encode(settings.clientId()) + "&client_secret=" + encode(settings.clientSecret())
                + "&redirect_uri=" + encode(settings.callback()) + "&code=" + encode(code)
                + "&code_verifier=" + encode(verifier), null);
        if (reply.status() != 200 || reply.body() == null) throw WaTeamRemote.unavailable();
        JsonNode body = reply.body();
        String value = body.path("access_token").asText("");
        var expires = body.path("expires_in");
        // Expiring tokens use GitHub App's default expiration setting. We do not request offline refresh.
        if (!value.matches("ghu_[A-Za-z0-9_]{16,250}") || !expires.isIntegralNumber() || !expires.canConvertToLong()
                || expires.longValue() <= 0 || !"bearer".equalsIgnoreCase(body.path("token_type").asText()))
            throw WaProblem.unauthorized();
        return new Token(value, expires.longValue());
    }

    Actor actor(Token token) {
        JsonNode body = api("/user", token);
        var id = body.path("id");
        String login = body.path("login").asText("");
        if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0
                || !login.matches("[A-Za-z0-9-]{1,39}") || !"User".equals(body.path("type").asText()))
            throw WaProblem.unauthorized();
        return new Actor(id.longValue(), login);
    }

    void requireRepositoryWrite(Token token) {
        JsonNode body = api("/user/installations/" + settings.installationId() + "/repositories?per_page=100", token);
        // This installation must be dedicated to exactly the team's repository.
        JsonNode repositories = body.path("repositories");
        JsonNode count = body.path("total_count");
        if (!count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() != 1 || !repositories.isArray() || repositories.size() != 1)
            throw WaProblem.denied();
        JsonNode repo = repositories.get(0);
        JsonNode permissions = repo.path("permissions");
        if (!REPOSITORY.equalsIgnoreCase(repo.path("full_name").asText())
                || !(permissions.path("push").equals(com.fasterxml.jackson.databind.node.BooleanNode.TRUE)
                    || permissions.path("maintain").equals(com.fasterxml.jackson.databind.node.BooleanNode.TRUE)
                    || permissions.path("admin").equals(com.fasterxml.jackson.databind.node.BooleanNode.TRUE)))
            throw WaProblem.denied();
    }

    private JsonNode api(String path, Token token) {
        var reply = remote.exchange(URI.create(API + path), Map.of("Authorization", "Bearer " + token.value,
            "Accept", "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28"), null, null);
        if (reply.status() == 401) throw WaProblem.unauthorized();
        if (reply.status() == 403 || reply.status() == 404) throw WaProblem.denied();
        if (reply.status() != 200 || reply.body() == null) throw WaTeamRemote.unavailable();
        return reply.body();
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
