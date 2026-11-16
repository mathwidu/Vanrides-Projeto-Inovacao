package com.vanrides.vanrides.whatsapp.privacy;

/** Scope is established by immutable routes/command origin; alias fallback is single-instance only. */
final class WaPrivacyContentQueries {
    private WaPrivacyContentQueries() { }
    static final String RETAIN_INBOX = """
        jsonb_strip_nulls(jsonb_build_object('senderAliasId',i.payload_json->'senderAliasId',
          'occurredAt',i.payload_json->'occurredAt','replyTo',i.payload_json->'replyTo'))
        """;
    private static final String NO_ORIGIN = """
        o.command_id IS NULL AND
        NOT EXISTS(SELECT 1 FROM wa.passenger_response_route r WHERE r.outbox_id=o.id) AND
        NOT EXISTS(SELECT 1 FROM wa.driver_attendance_notice r WHERE r.outbox_id=o.id) AND
        NOT EXISTS(SELECT 1 FROM wa.team_passenger_notice r WHERE r.outbox_id=o.id) AND
        NOT EXISTS(SELECT 1 FROM wa.approach_delivery_route r WHERE r.outbox_id=o.id) AND
        NOT EXISTS(SELECT 1 FROM wa.attendance_question q WHERE q.outbox_id=o.id)
        """;
    static final String CANDIDATES = """
        WITH retention_scope AS (SELECT ?::text environment,?::text instance_id,?::boolean synthetic_only,
          ?::timestamptz as_of,?::timestamptz cutoff), eligible AS (
          SELECT 'I' kind,i.id,i.created_at,false ambiguous,
            'I:'||to_char(i.created_at AT TIME ZONE 'UTC','YYYYMMDDHH24MISSUS')||':'||i.id::text work_key
          FROM wa.integration_inbox i JOIN wa.identity person ON person.id=i.identity_id
          JOIN wa.test_cohort cohort ON cohort.id=person.test_cohort_id CROSS JOIN retention_scope scope
          WHERE i.instance_id=scope.instance_id AND cohort.environment=scope.environment
            AND (NOT scope.synthetic_only OR cohort.synthetic) AND i.category IN ('MESSAGE','ECHO')
            AND i.created_at<=scope.cutoff AND i.payload_json<>
        """ + RETAIN_INBOX + """
          UNION ALL
          SELECT 'O',o.id,o.created_at,(
        """ + NO_ORIGIN + """
            AND EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=person.id AND a.instance_id<>scope.instance_id)),
            'O:'||to_char(o.created_at AT TIME ZONE 'UTC','YYYYMMDDHH24MISSUS')||':'||o.id::text
          FROM wa.message_outbox o JOIN wa.identity person ON person.id=o.recipient_identity_id
          JOIN wa.test_cohort cohort ON cohort.id=person.test_cohort_id CROSS JOIN retention_scope scope
          WHERE cohort.environment=scope.environment AND (NOT scope.synthetic_only OR cohort.synthetic)
            AND o.created_at<=scope.cutoff AND o.expires_at<=scope.as_of
            AND (o.payload_json<>'{}' OR o.private_payload_ciphertext IS NOT NULL OR o.start_payload_ciphertext IS NOT NULL)
            AND (
              EXISTS(SELECT 1 FROM wa.command_receipt command JOIN wa.integration_inbox source
                ON source.id=command.inbox_event_id WHERE command.command_id=o.command_id AND source.instance_id=scope.instance_id)
              OR EXISTS(SELECT 1 FROM wa.passenger_response_route route WHERE route.outbox_id=o.id
                AND route.environment=scope.environment AND route.instance_id=scope.instance_id)
              OR EXISTS(SELECT 1 FROM wa.driver_attendance_notice route WHERE route.outbox_id=o.id
                AND route.environment=scope.environment AND route.instance_id=scope.instance_id)
              OR EXISTS(SELECT 1 FROM wa.team_passenger_notice route WHERE route.outbox_id=o.id
                AND route.environment=scope.environment AND route.instance_id=scope.instance_id)
              OR EXISTS(SELECT 1 FROM wa.approach_delivery_route route WHERE route.outbox_id=o.id
                AND route.environment=scope.environment AND route.instance_id=scope.instance_id)
              OR EXISTS(SELECT 1 FROM wa.attendance_question question WHERE question.outbox_id=o.id
                AND question.environment=scope.environment AND question.instance_id=scope.instance_id)
              OR (
        """ + NO_ORIGIN + """
                AND EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=person.id AND a.instance_id=scope.instance_id))
            )
        )
        """;
}
