package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.config.TeamApiTestEnvironment;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class WaTeamApiBoundaryTest {
    private MockHttpServletRequest request(String method,String path) { return new MockHttpServletRequest(method,path); }
    @Test void healthAndOnlyTheFourExactWorkerRoutesReachExistingAdmission() throws Exception {
        for(String[] route:new String[][]{{"GET","/actuator/health"},{"GET","/integrations/whatsapp/v1/context"},{"POST","/integrations/whatsapp/v1/commands"},
            {"POST","/integrations/whatsapp/v1/inbox/claim"},{"POST","/integrations/whatsapp/v1/inbox/00000000-0000-4000-8000-000000000001/result"}}) {
            var response=new MockHttpServletResponse();assertThat(WaTeamApiBoundary.admit(request(route[0],route[1]),response)).isTrue();
            assertThat(response.getHeader("X-VanRides-Environment")).isEqualTo("vanrides-team-hml");assertThat(response.getHeader("X-VanRides-Revision")).isNull();
        }
    }
    @Test void legacyChannelGpsActuatorAndMethodOrPathVariantsAreDefaultDeniedBeforeBodyOrAuth() throws Exception {
        var filter=new WaIngressFilter(new WaSettings(TeamApiTestEnvironment.valid()),null,null,null);
        for(String[] route:new String[][]{{"GET","/motoristas"},{"POST","/passageiros"},{"POST","/login"},{"GET","/account/me"},{"GET","/actuator/env"},
            {"POST","/integrations/whatsapp/v1/events"},{"POST","/integrations/whatsapp/v1/outbox/claim"},{"POST","/integrations/whatsapp/v1/jobs/tick"},
            {"POST","/driver-app/v1/pairings/begin"},{"GET","/ws"},{"GET","/swagger-ui/index.html"},{"HEAD","/actuator/health"},{"OPTIONS","/actuator/health"},
            {"POST","/actuator/health"},{"GET","/actuator/health/"},{"GET","/actuator/health;v=x"},{"GET","/actuator//health"},{"GET","/%61ctuator/health"},
            {"GET","/integrations/whatsapp/v1/inbox/claim"},{"POST","/integrations/whatsapp/v1/context"}}) {
            var response=new MockHttpServletResponse();filter.doFilter(request(route[0],route[1]),response,(a,b)->{throw new AssertionError("Unexpected handler");});
            assertThat(response.getStatus()).as(route[1]).isEqualTo(404);assertThat(response.getHeader("X-VanRides-Environment")).isEqualTo("vanrides-team-hml");
        }
    }
    @Test void originsForwardsErrorsAndHealthQueryCannotBypassTheBoundary() throws Exception {
        var browser=request("GET","/actuator/health");browser.addHeader("Origin","https://example.invalid");
        var forward=request("GET","/actuator/health");forward.setDispatcherType(DispatcherType.FORWARD);
        var error=request("GET","/actuator/health");error.setDispatcherType(DispatcherType.ERROR);
        var query=request("GET","/actuator/health");query.setQueryString("show=details");
        for(var incoming:new MockHttpServletRequest[]{browser,forward,error,query}) assertThat(WaTeamApiBoundary.admit(incoming,new MockHttpServletResponse())).isFalse();
    }
    @Test void outsideTheModeLegacyRequestsHaveNoEnvironmentOrRequestSelectedRevisionMarker() throws Exception {
        var env=TeamApiTestEnvironment.valid().withProperty("vanrides.wa.team-api-only","false");var response=new MockHttpServletResponse();
        var incoming=request("GET","/motoristas");incoming.addHeader("X-VanRides-Revision","untrusted");
        var called=new java.util.concurrent.atomic.AtomicBoolean();
        new WaIngressFilter(new WaSettings(env),null,null,null).doFilter(incoming,response,(a,b)->called.set(true));
        assertThat(called).isTrue();assertThat(response.getHeader("X-VanRides-Environment")).isNull();assertThat(response.getHeader("X-VanRides-Revision")).isNull();
    }
}
