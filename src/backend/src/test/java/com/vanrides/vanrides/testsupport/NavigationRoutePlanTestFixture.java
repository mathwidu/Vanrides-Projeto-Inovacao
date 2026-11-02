package com.vanrides.vanrides.testsupport;

import com.vanrides.vanrides.dto.NavigationRoutePlanInstructionResponse;
import com.vanrides.vanrides.dto.NavigationRoutePlanLegResponse;
import com.vanrides.vanrides.dto.NavigationRoutePlanResponse;

import java.util.List;

public final class NavigationRoutePlanTestFixture {

    private NavigationRoutePlanTestFixture() {
    }

    public static NavigationRoutePlanResponse navigationRoutePlan() {
        return new NavigationRoutePlanResponse(
                "route-br-rs-001",
                "valhalla",
                "BR-RS",
                "van_school_safe",
                "validada",
                "ready",
                0.95,
                null,
                780,
                4200,
                List.of(List.of(-30.0346, -51.2177), List.of(-30.0350, -51.2180)),
                List.of(new NavigationRoutePlanLegResponse("driver", "stop-300", 2100, 390)),
                List.of(new NavigationRoutePlanInstructionResponse(
                        0,
                        "Siga pela Avenida Ipiranga.",
                        "Em 200 metros, siga pela Avenida Ipiranga.",
                        200,
                        "continue"
                ))
        );
    }
}
