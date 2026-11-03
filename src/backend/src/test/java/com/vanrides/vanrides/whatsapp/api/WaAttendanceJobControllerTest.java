package com.vanrides.vanrides.whatsapp.api;

import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceJobs;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.auth.WaRoutes;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaAttendanceJobControllerTest {
    private static final String PATH = "/integrations/whatsapp/v1/jobs/tick";
    private final WaAttendanceJobs jobs = mock(WaAttendanceJobs.class);
    private final WaAttendanceJobController controller = new WaAttendanceJobController(jobs);

    private MockHttpServletRequest request(WaPrincipal.Kind principal) {
        var request = new MockHttpServletRequest("POST", PATH);
        request.setAttribute(WaHttp.ROUTE, WaRoutes.resolve("POST", PATH));
        request.setAttribute(WaHttp.PRINCIPAL, new WaPrincipal(principal, UUID.randomUUID(), null, null, "synthetic", null, 0, false));
        request.setAttribute(WaHttp.BODY, "{}".getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test void rejectsUnexpectedQueryOrMissingTrustedBodyBeforeBusinessWork() {
        var query = request(WaPrincipal.Kind.SYSTEM_JOB);
        query.setQueryString("cursor=untrusted");
        var missing = request(WaPrincipal.Kind.SYSTEM_JOB);
        missing.removeAttribute(WaHttp.BODY);
        for (var invalid : new MockHttpServletRequest[]{query, missing}) {
            assertEquals(WaProblem.Code.INVALID_PAYLOAD, assertThrows(WaProblem.class, () -> controller.tick(invalid)).code());
        }
        verifyNoInteractions(jobs);
    }

    @Test void rejectsAnotherPrincipalOrRouteBeforeParsingInputs() {
        var otherRole = request(WaPrincipal.Kind.CHANNEL);
        var otherRoute = request(WaPrincipal.Kind.SYSTEM_JOB);
        otherRoute.setAttribute(WaHttp.ROUTE, WaRoutes.resolve("POST", "/integrations/whatsapp/v1/commands"));
        for (var invalid : new MockHttpServletRequest[]{otherRole, otherRoute}) {
            assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED, assertThrows(WaProblem.class, () -> controller.tick(invalid)).code());
        }
        verifyNoInteractions(jobs);
    }
}
