package com.vanrides.vanrides;

import com.vanrides.vanrides.config.UnreadRequestBodyValve;
import com.vanrides.vanrides.service.AuthService;
import com.vanrides.vanrides.filtros.BasicRateLimitFilter;
import com.vanrides.vanrides.filtros.RequestBodyLimitFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import org.apache.catalina.Context;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.baseline-on-migrate=false",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.show-sql=false",
                "vanrides.auth.jwt-secret=context-startup-test-secret-at-least-32-characters",
                "vanrides.rate-limit.enabled=false",
                "vanrides.cors.allowed-origin-patterns=https://app.example",
                "vanrides.push.provider=none"
        }
)
@Testcontainers(disabledWithoutDocker = true)
@Import(VanridesApplicationContextIntegrationTest.UnhandledServletFailureConfiguration.class)
class VanridesApplicationContextIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vanrides_application_context_test")
            .withUsername("vanrides_context_test")
            .withPassword("vanrides_context_test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int serverPort;

    @Autowired
    private AuthService authService;

    @Autowired
    private BasicRateLimitFilter basicRateLimitFilter;

    @Autowired
    private RequestBodyLimitFilter requestBodyLimitFilter;

    @Autowired
    private ServletWebServerApplicationContext webApplicationContext;

    @Test
    void shouldStartTheCompleteWebApplicationContext() {
        assertThat(serverPort).isPositive();
        assertThat(authService).isNotNull();
        assertThat(basicRateLimitFilter).isNotNull();
        assertThat(requestBodyLimitFilter).isNotNull();

        TomcatWebServer webServer = (TomcatWebServer) webApplicationContext.getWebServer();
        long unreadBodyValves = Arrays.stream(webServer.getTomcat().getHost().findChildren())
                .filter(Context.class::isInstance)
                .map(Context.class::cast)
                .flatMap(context -> Arrays.stream(context.getPipeline().getValves()))
                .filter(UnreadRequestBodyValve.class::isInstance)
                .count();
        assertThat(unreadBodyValves).isOne();
    }

    @Test
    void shouldPreserveEarlyJsonResponseWhenUnreadBodyClosesHttp11Connection() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + serverPort + "/driver-onboarding/requests"))
                .timeout(Duration.ofSeconds(5))
                .header("Origin", "https://app.example")
                .header("X-Request-Id", "context-form-rejection")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("a=b"))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).contains("\"code\":\"UNSUPPORTED_BODY_MEDIA_TYPE\"");
        assertThat(response.headers().firstValue("Connection")).contains("close");
        assertThat(response.headers().firstValue("X-Request-Id"))
                .contains("context-form-rejection");
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("https://app.example");
    }

    @Test
    void shouldLeaveUnhandledServletErrorResponseUncommittedForErrorDispatch() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + serverPort + "/__qa/unhandled"))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofString("unread-body"))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
        assertThat(response.headers().firstValue("Connection")).contains("close");
        assertThat(response.body()).contains("\"status\":500");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UnhandledServletFailureConfiguration {

        @Bean
        FilterRegistrationBean<Filter> unhandledServletFailureFilter() {
            Filter filter = (request, response, chain) -> {
                throw new ServletException("intentional integration-test failure");
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.setName("unhandledServletFailureFilter");
            registration.addUrlPatterns("/__qa/unhandled");
            registration.setDispatcherTypes(DispatcherType.REQUEST);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }
}
