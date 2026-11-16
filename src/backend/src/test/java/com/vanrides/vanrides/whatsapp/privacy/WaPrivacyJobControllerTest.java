package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.vanrides.vanrides.whatsapp.api.*;
import com.vanrides.vanrides.whatsapp.auth.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WaPrivacyJobControllerTest {
    private static final String PATH="/integrations/whatsapp/v1/privacy/tick";
    private final WaPrivacyJobs jobs=mock(WaPrivacyJobs.class);
    private final WaPrivacyJobController controller=new WaPrivacyJobController(jobs);
    private MockHttpServletRequest request(){
        var request=new MockHttpServletRequest("POST",PATH);request.setAttribute(WaHttp.ROUTE,WaRoutes.resolve("POST",PATH));
        request.setAttribute(WaHttp.PRINCIPAL,new WaPrincipal(WaPrincipal.Kind.SYSTEM_JOB,UUID.randomUUID(),null,null,"synthetic",null,0,false));
        request.setAttribute(WaHttp.BODY,"{\"family\":\"TECHNICAL_7D\",\"batchLimit\":20}".getBytes(StandardCharsets.UTF_8));return request;
    }
    @Test void routeIsPrivateSystemJobAndAvailableWhileStartsAreDisabled(){
        var route=WaRoutes.resolve("POST",PATH);assertThat(route.kind()).isEqualTo(WaPrincipal.Kind.SYSTEM_JOB);
        assertThat(route.available()).isTrue();assertThat(route.allowWhileDisabled()).isTrue();assertThat(WaRoutes.resolve("GET",PATH)).isNull();
    }
    @Test void requiresMiddlewareRouteAndRole(){
        var request=request();request.removeAttribute(WaHttp.ROUTE);assertThatThrownBy(()->controller.tick(request)).isInstanceOf(WaProblem.class);
        request.setAttribute(WaHttp.ROUTE,WaRoutes.resolve("POST",PATH));request.setAttribute(WaHttp.PRINCIPAL,new WaPrincipal(WaPrincipal.Kind.CHANNEL,UUID.randomUUID(),null,null,"synthetic",null,0,false));
        assertThatThrownBy(()->controller.tick(request)).isInstanceOf(WaProblem.class);verifyNoInteractions(jobs);
    }
    @Test void rejectsQueryAndMissingAuthenticatedBody(){
        var request=request();request.setQueryString("family=PROFILE_30D");assertThatThrownBy(()->controller.tick(request)).isInstanceOf(WaProblem.class);
        request.setQueryString(null);request.removeAttribute(WaHttp.BODY);assertThatThrownBy(()->controller.tick(request)).isInstanceOf(WaProblem.class);verifyNoInteractions(jobs);
    }
    @Test void delegatesOnlyTheParsedClosedBody(){
        var result=WaJson.object("{\"status\":\"DRY_RUN\"}");when(jobs.tick(any(),any())).thenReturn(result);
        assertThat(controller.tick(request())).isSameAs(result);
        verify(jobs).tick(any(),argThat(body->body.size()==2 && body.path("family").asText().equals("TECHNICAL_7D") && body.path("batchLimit").intValue()==20));
    }
    @Test void realIngressAllowsOnlySystemJobEvenWithNewEffectsDisabled() throws Exception {
        var settings=new com.vanrides.vanrides.whatsapp.config.WaSettings(new org.springframework.mock.env.MockEnvironment()
            .withProperty("vanrides.wa.enabled","false").withProperty("vanrides.wa.environment","privacy-test")
            .withProperty("vanrides.wa.public-base-url","http://127.0.0.1:8080").withProperty("vanrides.wa.hash-key",key(1))
            .withProperty("vanrides.wa.response-key",key(2)).withProperty("vanrides.wa.operator-credential",key(3))
            .withProperty("vanrides.wa.operator-expires-at","2099-01-01T00:00:00Z"));
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var credentials=new WaCredentials(jdbc,settings,new com.vanrides.vanrides.whatsapp.config.WaClock());
        var filter=new WaIngressFilter(settings,credentials,new WaRateLimiter(),new com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver("",1024,10));
        for(var kind:new WaPrincipal.Kind[]{WaPrincipal.Kind.SYSTEM_JOB,WaPrincipal.Kind.CHANNEL,WaPrincipal.Kind.CONVERSATION_WORKER}){
            doReturn(java.util.List.of(new WaPrincipal(kind,UUID.randomUUID(),null,null,"synthetic",null,0,false))).when(jdbc)
                .query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class));
            var request=new MockHttpServletRequest("POST",PATH);request.setRemoteAddr("127.0.0.1");request.setContentType("application/json");request.addHeader("Authorization","Bearer "+WaCrypto.randomToken());
            var response=new org.springframework.mock.web.MockHttpServletResponse();var reached=new java.util.concurrent.atomic.AtomicBoolean();
            filter.doFilter(request,response,(a,b)->reached.set(true));
            assertThat(reached.get()).isEqualTo(kind==WaPrincipal.Kind.SYSTEM_JOB);
            if(kind!=WaPrincipal.Kind.SYSTEM_JOB)assertThat(response.getStatus()).isEqualTo(403);
        }
    }
    private static String key(int value){byte[] key=new byte[32];java.util.Arrays.fill(key,(byte)value);return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(key);}
}
