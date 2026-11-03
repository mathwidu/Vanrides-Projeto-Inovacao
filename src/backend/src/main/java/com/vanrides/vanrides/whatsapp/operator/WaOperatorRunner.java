package com.vanrides.vanrides.whatsapp.operator;

import java.nio.file.Path;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class WaOperatorRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaOperatorRunner.class);
    private final Environment environment;
    private final WaOperatorMode mode;
    private final WaOperatorService service;
    private final Function<Path, WaOperatorFiles> filesFactory;
    @Autowired
    public WaOperatorRunner(Environment environment, WaOperatorMode mode, WaOperatorService service) {
        this(environment, mode, service, WaOperatorFiles::create);
    }
    WaOperatorRunner(Environment environment, WaOperatorMode mode, WaOperatorService service,
            Function<Path, WaOperatorFiles> filesFactory) {
        this.environment = environment; this.mode = mode; this.service = service;
        this.filesFactory = filesFactory;
    }
    @Override public void run(ApplicationArguments arguments) {
        String input = environment.getProperty("vanrides.wa.operator.request-file");
        if (input == null) return;
        try {
            mode.requireLocal();
            String destination = environment.getProperty("vanrides.wa.operator.output-file");
            if (destination == null || input.isBlank() || destination.isBlank()) throw new WaOperatorFailure();
            WaOperatorRequest request = WaOperatorRequest.parse(WaOperatorFiles.read(Path.of(input)));
            try (WaOperatorFiles files = filesFactory.apply(Path.of(destination))) {
                service.execute(request, files::prepare);
                files.committed();
            }
            LOGGER.info("WA_OPERATOR_OK");
        } catch (Exception failure) {
            LOGGER.warn("WA_OPERATOR_FAILED");
            throw new WaOperatorFailure();
        }
    }
}
