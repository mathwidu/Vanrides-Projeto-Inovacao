package com.vanrides.vanrides.whatsapp.team;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaTeamProvidersTest {
    @Test
    void githubChecksTheConfiguredInstallationAndRepositoryRoleWithoutBroadOauthScopes() throws Exception {
        var settings = mock(WaTeamSettings.class);
        when(settings.installationId()).thenReturn("12345");
        when(settings.clientId()).thenReturn("Iv23synthetic");
        when(settings.callback()).thenReturn("https://api.example.invalid/equipe/callback");
        var remote = mock(WaTeamRemote.class);
        var github = new WaTeamGitHub(settings, remote);
        var token = new WaTeamGitHub.Token("ghu_synthetic-token", 28800);
        assertThat(github.login("synthetic-state", "synthetic-challenge"))
            .startsWith("https://github.com/login/oauth/authorize?")
            .contains("code_challenge_method=S256", "state=synthetic-state", "redirect_uri=https%3A%2F%2Fapi.example.invalid%2Fequipe%2Fcallback")
            .doesNotContain("scope=", "public_repo");

        var endpoint = URI.create("https://api.github.com/user/installations/12345/repositories?per_page=100");
        var headers = Map.of("Authorization", "Bearer ghu_synthetic-token", "Accept", "application/vnd.github+json",
            "X-GitHub-Api-Version", "2022-11-28");
        when(remote.exchange(endpoint, headers, null, null))
            .thenReturn(githubRepo("mathwidu/Vanrides-Projeto-Inovacao", "pull"))
            .thenReturn(githubRepo("someone/other", "push"))
            .thenReturn(githubRepo("mathwidu/Vanrides-Projeto-Inovacao", "push"))
            .thenReturn(githubRepo("mathwidu/Vanrides-Projeto-Inovacao", "maintain"))
            .thenReturn(githubRepo("mathwidu/Vanrides-Projeto-Inovacao", "admin"))
            .thenReturn(new WaTeamRemote.Reply(401, null));
        assertThatThrownBy(() -> github.requireRepositoryWrite(token)).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> github.requireRepositoryWrite(token)).isInstanceOf(WaProblem.class);
        for (int i = 0; i < 3; i++) assertThatCode(() -> github.requireRepositoryWrite(token)).doesNotThrowAnyException();
        assertThatThrownBy(() -> github.requireRepositoryWrite(token))
            .isInstanceOfSatisfying(WaProblem.class, failure -> assertThat(failure.code()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL));
    }

    @Test
    void evolutionRequiresAnExistingCanonicalPnAndOnlyUsesProviderReturnedLid() throws Exception {
        var pnOnly = WaTeamEvolution.verified("5511990001234", response("""
            [{"exists":true,"number":"5511990001234","jid":"551190001234@s.whatsapp.net"}]
            """));
        assertThat(pnOnly.inputPn).isEqualTo("5511990001234@s.whatsapp.net");
        assertThat(pnOnly.pn).isEqualTo("551190001234@s.whatsapp.net");
        assertThat(pnOnly.lid).isNull();
        var paired = WaTeamEvolution.verified("12025550101", response("""
            [{"exists":true,"number":"12025550101","jid":"12025550101@s.whatsapp.net","lid":"1234@lid"}]
            """));
        assertThat(paired.lid).isEqualTo("1234@lid");
        assertThatThrownBy(() -> WaTeamEvolution.verified("12025550101", response("""
            [{"exists":true,"jid":"12025550102@s.whatsapp.net"}]
            """))).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> WaTeamEvolution.verified("12025550101", response("""
            [{"exists":true,"jid":"12025550101@s.whatsapp.net","lid":"12025550101@s.whatsapp.net"}]
            """))).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> WaTeamEvolution.verified("12025550101", response("""
            [{"exists":false}]
            """))).isInstanceOfSatisfying(WaTeamProblem.class, problem -> assertThat(problem.code).isEqualTo(WaTeamProblem.Code.WHATSAPP_NOT_FOUND));
    }

    private static WaTeamRemote.Reply githubRepo(String name, String role) {
        var payload = WaJson.MAPPER.createObjectNode().put("total_count", 1);
        payload.putArray("repositories").addObject().put("full_name", name).putObject("permissions").put(role, true);
        return new WaTeamRemote.Reply(200, payload);
    }

    private static WaTeamRemote.Reply response(String json) throws Exception {
        return new WaTeamRemote.Reply(200, WaJson.MAPPER.readTree(json));
    }
}
