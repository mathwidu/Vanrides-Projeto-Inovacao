package com.vanrides.vanrides.whatsapp.transport;

/** Durable origin lookup remains valid after business eligibility changes. */
final class WaDeliveryOrigins {
    static final String SCOPE =
            " CROSS JOIN (SELECT ?::text AS instance_id, ?::text AS environment) delivery_scope ";
    static final String ELIGIBLE = """
        o.payload_kind='TEXT' AND (
          (o.purpose='RESPONSE' AND EXISTS (
            SELECT 1 FROM wa.command_receipt r JOIN wa.integration_inbox e
              ON e.id=r.inbox_event_id AND e.identity_id=r.identity_id
            WHERE r.command_id=o.command_id AND e.provider='EVOLUTION'
              AND e.instance_id=delivery_scope.instance_id AND
        (""" + WaFoundationResponses.ELIGIBLE + """
        OR (r.identity_id=o.recipient_identity_id AND o.policy_version='run-gps-v1'
          AND o.response_part IN ('run-gps-confirmed','run-gps-unavailable')
          AND r.command_type IN ('REQUEST_START','REQUEST_RESUME','ADVANCE_RUN_SETUP')
          AND EXISTS(SELECT 1 FROM wa.start_intent si WHERE si.request_command_id=r.command_id
            AND si.run_id=o.run_id AND si.driver_id=o.recipient_identity_id
            AND si.environment=delivery_scope.environment)))
              AND (o.policy_version<>'passenger-setup-v1' OR EXISTS (
                SELECT 1 FROM wa.passenger_response_route pr WHERE pr.outbox_id=o.id
                  AND pr.environment=delivery_scope.environment))
          )) OR
          (o.purpose='REMINDER' AND o.policy_version='attendance-reminder-v1' AND o.command_id IS NULL
            AND EXISTS (SELECT 1 FROM wa.attendance_question q JOIN wa.service_credential job
              ON job.id=q.job_credential_id AND job.principal='SYSTEM_JOB'
              AND job.environment=q.environment AND job.allowed_instance_id=q.instance_id
              WHERE q.outbox_id=o.id
              AND q.passenger_id=o.recipient_identity_id AND q.occurrence_id=o.occurrence_id
              AND q.instance_id=delivery_scope.instance_id AND q.environment=delivery_scope.environment))
        )
        """;
    static final String SOURCE =
            "FROM wa.message_outbox o " + SCOPE + " WHERE " + ELIGIBLE;

    private WaDeliveryOrigins() {}
}
