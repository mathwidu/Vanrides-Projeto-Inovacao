package com.vanrides.vanrides.service;

import com.vanrides.vanrides.config.TeamApiTestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ConfiguredEmailDeliveryGatewayTest {
    private final LoggingEmailDeliveryGateway logging=mock(LoggingEmailDeliveryGateway.class);
    private final ResendEmailDeliveryGateway resend=mock(ResendEmailDeliveryGateway.class);
    private ConfiguredEmailDeliveryGateway gateway(String provider,org.springframework.core.env.Environment env) {
        var gateway=new ConfiguredEmailDeliveryGateway();ReflectionTestUtils.setField(gateway,"provider",provider);
        ReflectionTestUtils.setField(gateway,"environment",env);ReflectionTestUtils.setField(gateway,"loggingEmailDeliveryGateway",logging);
        ReflectionTestUtils.setField(gateway,"resendEmailDeliveryGateway",resend);return gateway;
    }
    @Test void disabledNeverSendsOrLogsAnyEmailContent(CapturedOutput output) {
        var gateway=gateway("disabled",TeamApiTestEnvironment.valid());gateway.validateConfiguration();
        assertThatThrownBy(()->gateway.sendEmail("PRIVATE_RECIPIENT","PRIVATE_SUBJECT","PRIVATE_BODY","PRIVATE_TOKEN"))
            .isInstanceOf(IllegalStateException.class).hasMessage("EMAIL_DELIVERY_DISABLED");
        verifyNoInteractions(logging,resend);assertThat(output.getAll()).doesNotContain("PRIVATE_RECIPIENT","PRIVATE_SUBJECT","PRIVATE_BODY","PRIVATE_TOKEN");
    }
    @Test void disabledAndUnknownProvidersFailOutsideTheOnlyAuthorizedMode() {
        var env=TeamApiTestEnvironment.valid().withProperty("vanrides.wa.team-api-only","false");
        for(String provider:new String[]{"disabled","disabeld","smtp"}) assertThatThrownBy(()->gateway(provider,env).validateConfiguration()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(logging,resend);
    }
    @Test void theLegacyLogProviderStillUsesOnlyItsExistingAdapter() {
        var gateway=gateway("log",new org.springframework.mock.env.MockEnvironment());gateway.validateConfiguration();
        gateway.sendEmail("synthetic@example.invalid","subject","html","text");
        verify(logging).sendEmail("synthetic@example.invalid","subject","html","text");verifyNoInteractions(resend);
    }
    @Test void configuredResendKeepsItsExistingAdapterOutsideTheTeamMode() {
        when(resend.isConfigured()).thenReturn(true);var gateway=gateway("resend",new org.springframework.mock.env.MockEnvironment());gateway.validateConfiguration();
        gateway.sendEmail("synthetic@example.invalid","subject","html","text");verify(resend).sendEmail("synthetic@example.invalid","subject","html","text");verifyNoInteractions(logging);
    }
}
