package com.vanrides.vanrides;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class VanridesApplicationMainTest {
    @ParameterizedTest
    @CsvSource({"none, true, true", "none, false, false", "servlet, false, false",
        "servlet, true, false", "reactive, true, false"})
    void onlyCompletedExplicitNonWebOperatorInvocationClosesContext(
            String webType, boolean operatorRequested, boolean shouldClose) {
        var environment = new MockEnvironment().withProperty("spring.main.web-application-type", webType);
        if (operatorRequested) environment.setProperty("vanrides.wa.operator.request-file", "/synthetic/request.json");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.refresh();
            VanridesApplication.closeCompletedOperator(context);
            assertEquals(!shouldClose, context.isActive());
        }
    }
}
