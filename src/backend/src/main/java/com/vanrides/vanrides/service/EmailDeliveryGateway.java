package com.vanrides.vanrides.service;

public interface EmailDeliveryGateway {

    void sendEmail(String toEmail, String subject, String htmlBody, String textBody);
}
