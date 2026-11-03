package com.vanrides.vanrides.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggingEmailDeliveryGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailDeliveryGateway.class);

    public void sendEmail(String toEmail, String subject, String htmlBody, String textBody) {
        log.info("[EMAIL][DEV] Entrega simulada registrada sem destinatario ou conteudo.");
    }
}
