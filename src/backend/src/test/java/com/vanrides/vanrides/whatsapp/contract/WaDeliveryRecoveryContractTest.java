package com.vanrides.vanrides.whatsapp.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WaDeliveryRecoveryContractTest {
    @Test void implementedJobExamplesUseTheCanonicalUtcMinuteAcceptedByTheRuntime() throws Exception {
        var root = Path.of("contracts", "whatsapp-mvp"); var mapper = new ObjectMapper();
        var contract = mapper.readTree(root.resolve("openapi.yaml").toFile());
        var catalog = mapper.readTree(root.resolve("fixtures/catalog.json").toFile());
        int checked = 0;
        for (var fixture : catalog.path("fixtures")) {
            var value = fixture.path("value");
            if (fixture.path("schema").asText().equals("JobTickRequest")
                    && Set.of("MATERIALIZE_OCCURRENCES", "DUE_REMINDERS", "HEALTH_RECONCILE").contains(value.path("jobType").asText())) {
                String window = value.path("windowKey").asText(); Instant instant = Instant.parse(window);
                assertThat(instant.toString()).isEqualTo(window);
                assertThat(instant.truncatedTo(ChronoUnit.MINUTES)).isEqualTo(instant);
                assertThat(contract.at("/components/examples/" + fixture.path("id").asText() + "/value")).isEqualTo(value);
                assertThat(ContractFixtureValidator.validate(contract, "JobTickRequest", value)).isEmpty(); checked++;
            }
        }
        assertThat(checked).isEqualTo(3);
    }
}
