package com.vanrides.vanrides.whatsapp.operator;

import org.springframework.context.ApplicationContext;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
public final class WaOperatorMode {
    private final ApplicationContext context;
    private final Environment environment;
    public WaOperatorMode(ApplicationContext context, Environment environment) {
        this.context = context; this.environment = environment;
    }
    void requireLocal() {
        for (ApplicationContext current = context; current != null; current = current.getParent())
            if (current instanceof WebApplicationContext || current instanceof WebServerApplicationContext)
                throw new WaOperatorFailure();
        if (!"none".equals(environment.getProperty("spring.main.web-application-type")))
            throw new WaOperatorFailure();
        for (String profile : environment.getActiveProfiles())
            if (profile.toLowerCase(java.util.Locale.ROOT).startsWith("prod")) throw new WaOperatorFailure();
    }
    void requireOnline() {
        requireLocal();
        try {
            if (!com.vanrides.vanrides.config.MvpOnlineMode.enabled(environment)) throw new WaOperatorFailure();
            com.vanrides.vanrides.config.MvpOnlineMode.validate(environment);
        } catch (RuntimeException failure) { throw new WaOperatorFailure(); }
    }
}
