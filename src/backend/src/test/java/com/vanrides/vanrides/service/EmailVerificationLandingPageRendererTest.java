package com.vanrides.vanrides.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationLandingPageRendererTest {

    private final EmailVerificationLandingPageRenderer renderer =
            new EmailVerificationLandingPageRenderer();

    @Test
    void shouldBindCspHashesToTheExactInlineStyleAndScript() throws Exception {
        String page = renderer.render();
        String style = between(page, "<style>", "</style>");
        String script = between(page, "<script>", "</script>");

        assertThat(renderer.contentSecurityPolicy())
                .contains("style-src 'sha256-" + sha256(style) + "'")
                .contains("script-src 'sha256-" + sha256(script) + "'")
                .contains("default-src 'none'")
                .contains("connect-src 'self'")
                .contains("base-uri 'none'")
                .contains("form-action 'none'")
                .contains("frame-ancestors 'none'");
    }

    @Test
    void shouldRequireAnExplicitButtonActivationBeforePosting() {
        String page = renderer.render();
        String script = between(page, "<script>", "</script>");

        assertThat(script)
                .contains("addEventListener(\"click\"")
                .contains("fetch(window.location.pathname")
                .contains("credentials: \"omit\"")
                .contains("cache: \"no-store\"")
                .contains("referrerPolicy: \"no-referrer\"")
                .contains("redirect: \"error\"")
                .contains("new AbortController()")
                .contains("window.setTimeout(() => controller.abort(), 15000)")
                .contains("signal: controller.signal")
                .contains("window.clearTimeout(timeoutId)")
                .contains("response.status === 200")
                .contains("response.headers.get(\"content-type\")")
                .contains("responseType.split(\";\", 1)[0].trim().toLowerCase()")
                .contains("responseMediaType === \"application/json\"")
                .contains("payload.message === \"E-mail verificado com sucesso.\"")
                .contains("response.status === 400")
                .contains("response.status === 429")
                .contains("token = \"\"")
                .contains("kind === \"error\" ? \"assertive\" : \"polite\"")
                .contains("history.replaceState")
                .contains("new URLSearchParams(window.location.hash.slice(1)).get(\"token\")")
                .contains("new URLSearchParams(window.location.search).has(\"token\")")
                .doesNotContain("new URLSearchParams(window.location.search).get(\"token\")")
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage")
                .doesNotContain("document.cookie")
                .doesNotContain("console.");
        assertThat(script)
                .doesNotContain(".startsWith(")
                .doesNotContain("application/jsonp")
                .doesNotContain("application/json-evil");
        assertThat(script).doesNotContain("response.status >= 400 && response.status < 500");
        assertThat(script).doesNotContain("response.ok");
        assertThat(script.indexOf("history.replaceState"))
                .isLessThan(script.indexOf("addEventListener(\"click\""));
        assertThat(script.indexOf("addEventListener(\"click\""))
                .isLessThan(script.indexOf("fetch(window.location.pathname"));
    }

    @Test
    void shouldExposeAccessibleResponsiveStatesWithoutThirdParties() {
        String page = renderer.render();

        assertThat(page)
                .contains("<html lang=\"pt-BR\"")
                .contains("<main")
                .contains("<h1")
                .contains("<button id=\"confirm-button\" type=\"button\"")
                .contains("role=\"status\"")
                .contains("aria-live=\"polite\"")
                .contains("aria-busy=\"false\"")
                .contains("@media (prefers-reduced-motion: reduce)")
                .contains("@media (max-width: 20rem)")
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContain(" src=")
                .doesNotContain("<form");
    }

    private String between(String value, String start, String end) {
        int contentStart = value.indexOf(start) + start.length();
        return value.substring(contentStart, value.indexOf(end, contentStart));
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
