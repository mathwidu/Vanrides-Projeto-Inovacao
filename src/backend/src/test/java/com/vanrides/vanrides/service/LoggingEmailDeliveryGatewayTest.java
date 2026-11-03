package com.vanrides.vanrides.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingEmailDeliveryGatewayTest {

    @Test
    void shouldLogOnlyAnAggregateEventWithoutRecipientOrContent() {
        String recipient = "private.user@vanrides.test";
        String secret = "verification-secret-123";
        ListAppender<ILoggingEvent> appender = attach();

        try {
            new LoggingEmailDeliveryGateway().sendEmail(
                    recipient,
                    "Confirme seu e-mail",
                    "<a href=\"https://example.test/#token=" + secret + "\">Confirmar</a>",
                    "https://example.test/#token=" + secret
            );

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .isNotEmpty()
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(recipient)
                            .doesNotContain(secret)
                            .doesNotContain("Confirme seu e-mail")
                            .doesNotContain("example.test"));
        } finally {
            detach(appender);
        }
    }

    private ListAppender<ILoggingEvent> attach() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailDeliveryGateway.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detach(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailDeliveryGateway.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
