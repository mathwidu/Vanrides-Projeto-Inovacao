package com.vanrides.vanrides.whatsapp.transport;

/** Durable origin lookup remains valid after business eligibility changes. */
final class WaDeliveryOrigins {
    static final String SCOPE =
            " CROSS JOIN (SELECT ?::text AS instance_id, ?::text AS environment) delivery_scope ";
    static final String ELIGIBLE = """
        o.payload_kind='TEXT' AND (
          (o.purpose IN ('RESPONSE','PIN') AND EXISTS (
            SELECT 1 FROM wa.command_receipt r JOIN wa.integration_inbox e
              ON e.id=r.inbox_event_id AND e.identity_id=r.identity_id
            WHERE r.command_id=o.command_id AND e.provider='EVOLUTION'
              AND e.instance_id=delivery_scope.instance_id AND
        (""" + WaFoundationResponses.ELIGIBLE + """
        OR (o.purpose='RESPONSE' AND o.policy_version='driver-setup-v1' AND o.response_part='driver-invite-passenger'
          AND r.command_type='CREATE_INVITE' AND e.category='MESSAGE' AND r.identity_id<>o.recipient_identity_id
          AND EXISTS(SELECT 1 FROM wa.driver_invite i
            JOIN wa.identity d ON d.id=i.driver_id
            JOIN wa.identity p ON p.id=i.onboarding_identity_id AND p.test_cohort_id=d.test_cohort_id
            JOIN wa.test_cohort c ON c.id=d.test_cohort_id
            WHERE i.id=o.private_invite_id AND i.driver_id=r.identity_id AND p.id=o.recipient_identity_id
              AND NOT i.allow_first_contact AND c.environment=delivery_scope.environment))
        OR (r.identity_id=o.recipient_identity_id AND o.policy_version='driver-location-v1'
          AND o.response_part='driver-location' AND r.command_type='GET_DRIVER_LOCATION')
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
          (o.purpose='APPROACH' AND o.command_id IS NULL AND EXISTS (
            SELECT 1 FROM wa.approach_delivery_route route WHERE route.outbox_id=o.id
              AND route.instance_id=delivery_scope.instance_id AND route.environment=delivery_scope.environment)) OR
          (o.purpose='REMINDER' AND o.policy_version='attendance-reminder-v1' AND o.command_id IS NULL
            AND EXISTS (SELECT 1 FROM wa.attendance_question q JOIN wa.service_credential job
              ON job.id=q.job_credential_id AND job.principal='SYSTEM_JOB'
              AND job.environment=q.environment AND job.allowed_instance_id=q.instance_id
              WHERE q.outbox_id=o.id
              AND q.passenger_id=o.recipient_identity_id AND q.occurrence_id=o.occurrence_id
              AND q.instance_id=delivery_scope.instance_id AND q.environment=delivery_scope.environment)) OR
        """ + WaTeamPassengerNotices.ELIGIBLE + """
        )
        """;
    static final String SOURCE =
            "FROM wa.message_outbox o " + SCOPE + " WHERE " + ELIGIBLE;

    private WaDeliveryOrigins() {}
}
