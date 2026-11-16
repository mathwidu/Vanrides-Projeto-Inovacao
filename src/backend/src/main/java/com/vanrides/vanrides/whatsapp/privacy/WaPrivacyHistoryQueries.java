package com.vanrides.vanrides.whatsapp.privacy;

/** No cascade/delete of queue, replay, device, profile or tracking controls belongs to this slice. */
final class WaPrivacyHistoryQueries {
    private WaPrivacyHistoryQueries() { }
    static final String CANDIDATES = """
        WITH scope AS (SELECT ?::text environment,?::text instance_id,?::boolean synthetic_only,?::timestamptz cutoff),
        people AS (
          SELECT i.id
          FROM wa.identity i JOIN wa.test_cohort c ON c.id=i.test_cohort_id CROSS JOIN scope s
          WHERE c.environment=s.environment AND (NOT s.synthetic_only OR c.synthetic)
        ), days AS (
          SELECT o.*,r.id run_id,r.completed_at,
            greatest(o.scheduled_at,r.completed_at) fact_at,
            (r.id IS NULL OR r.completed_at IS NOT NULL) terminal_at_known,
            o.history_origin_bound_at IS NOT NULL AND o.history_environment=s.environment AND o.history_instance_id=s.instance_id origin_preserved,
            EXISTS(SELECT 1 FROM wa.privacy_history_sources(o.id) source WHERE source.environment IS DISTINCT FROM s.environment
              OR source.instance_id IS DISTINCT FROM s.instance_id) conflicting_origin,
            r.history_erased_at run_history_erased
          FROM wa.routine_occurrence o LEFT JOIN wa.operation_run r ON r.occurrence_id=o.id CROSS JOIN scope s
          WHERE o.scheduled_at<=s.cutoff AND greatest(o.scheduled_at,r.completed_at)<=s.cutoff
            AND (r.state IN ('COMPLETED','CANCELED') OR r.id IS NULL)
        ), eligible AS (
          SELECT '01'::text family,q.id,greatest(d.fact_at,q.created_at,q.answer_until,q.answered_at) fact_at,
            CASE WHEN NOT d.terminal_at_known THEN 'MISSING_TERMINAL_AT'
              WHEN NOT d.origin_preserved THEN 'HISTORY_ORIGIN_UNATTRIBUTABLE'
              WHEN d.conflicting_origin THEN 'CONFLICTING_PROVENANCE'
              WHEN q.outbox_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM wa.message_outbox box WHERE box.id=q.outbox_id
                AND box.state NOT IN ('PENDING','LEASED') AND box.expires_at<=s.cutoff+INTERVAL '90 days'
                AND box.payload_json='{}' AND box.private_payload_ciphertext IS NULL AND box.start_payload_ciphertext IS NULL
                AND (box.lease_expires_at IS NULL OR box.lease_expires_at<=s.cutoff+INTERVAL '90 days')
                AND NOT EXISTS(SELECT 1 FROM wa.message_attempt a WHERE a.outbox_id=box.id AND a.state='SUBMITTING'))
                THEN 'QUESTION_OUTBOX_PROVENANCE' END::text reason
          FROM wa.attendance_question q JOIN days d ON d.id=q.occurrence_id
          JOIN people passenger ON passenger.id=q.passenger_id JOIN people driver ON driver.id=q.driver_id CROSS JOIN scope s
          WHERE q.environment=s.environment AND q.instance_id=s.instance_id
            AND greatest(d.fact_at,q.created_at,q.answer_until,q.answered_at)<=s.cutoff
          UNION ALL
          SELECT '02',a.id,greatest(d.fact_at,a.created_at,a.answered_at),
            CASE WHEN NOT d.terminal_at_known THEN 'MISSING_TERMINAL_AT'
              WHEN NOT d.origin_preserved THEN 'HISTORY_ORIGIN_UNATTRIBUTABLE'
              WHEN d.conflicting_origin THEN 'CONFLICTING_PROVENANCE'
              WHEN EXISTS(SELECT 1 FROM wa.attendance_question q WHERE q.attendance_id=a.id) THEN 'QUESTION_REFERENCE'
              END
          FROM wa.attendance a JOIN days d ON d.routine_id=a.routine_id AND d.local_date=a.local_date
          JOIN people passenger ON passenger.id=a.passenger_id JOIN people driver ON driver.id=d.driver_id CROSS JOIN scope s
          WHERE greatest(d.fact_at,a.created_at,a.answered_at)<=s.cutoff
            AND (EXISTS(SELECT 1 FROM wa.attendance_question q WHERE q.attendance_id=a.id
                 AND q.environment=s.environment AND q.instance_id=s.instance_id)
              OR NOT EXISTS(SELECT 1 FROM wa.attendance_question q WHERE q.attendance_id=a.id)
                 AND (d.origin_preserved OR NOT d.conflicting_origin))
          UNION ALL
          SELECT '03',r.id,greatest(d.fact_at,r.created_at),
            CASE WHEN NOT d.terminal_at_known THEN 'MISSING_TERMINAL_AT'
              WHEN NOT d.origin_preserved THEN 'HISTORY_ORIGIN_UNATTRIBUTABLE'
              WHEN d.conflicting_origin THEN 'CONFLICTING_PROVENANCE' END
          FROM wa.occurrence_roster_override r JOIN days d ON d.id=r.occurrence_id
          JOIN people passenger ON passenger.id=r.passenger_id JOIN people driver ON driver.id=r.driver_id
          JOIN wa.integration_inbox source ON source.id=r.source_inbox_event_id CROSS JOIN scope s
          WHERE source.instance_id=s.instance_id AND greatest(d.fact_at,r.created_at)<=s.cutoff
          UNION ALL
          SELECT '04',support.id,support.closed_at,
            CASE WHEN support.purpose='CONVERSATION' AND source.id IS NULL AND direct.id IS NULL THEN 'SUPPORT_ORIGIN_UNATTRIBUTABLE'
              WHEN source.id IS NOT NULL AND direct.id IS NOT NULL AND source.instance_id IS DISTINCT FROM direct.instance_id
                THEN 'CONFLICTING_PROVENANCE' END
          FROM wa.support_case support JOIN people person ON person.id=support.identity_id
          LEFT JOIN wa.command_receipt command ON command.command_id::text=support.correlation_id AND command.identity_id=support.identity_id
          LEFT JOIN wa.integration_inbox source ON source.id=command.inbox_event_id
          LEFT JOIN wa.integration_inbox direct ON direct.id::text=support.correlation_id AND direct.identity_id=support.identity_id
          CROSS JOIN scope s
          WHERE support.state='CLOSED' AND support.closed_at<=s.cutoff AND support.created_at<=s.cutoff
            AND (support.purpose='DELIVERY' AND support.environment=s.environment AND support.instance_id=s.instance_id
              OR support.purpose='CONVERSATION' AND (source.instance_id=s.instance_id OR direct.instance_id=s.instance_id
                OR source.id IS NULL AND direct.id IS NULL))
          UNION ALL
          SELECT '05',audit.id,audit.created_at,
            CASE WHEN person.id IS NULL THEN 'AUDIT_COHORT_UNATTRIBUTABLE'
              WHEN source.id IS NULL AND direct.id IS NULL AND credential.allowed_instance_id IS NULL THEN 'AUDIT_ORIGIN_UNATTRIBUTABLE'
              WHEN source.id IS NOT NULL AND source.instance_id IS DISTINCT FROM s.instance_id
                OR direct.id IS NOT NULL AND direct.instance_id IS DISTINCT FROM s.instance_id
                OR credential.allowed_instance_id IS NOT NULL AND credential.allowed_instance_id IS DISTINCT FROM s.instance_id
                THEN 'CONFLICTING_PROVENANCE' END
          FROM wa.audit_event audit LEFT JOIN people person ON person.id=audit.actor_identity_id
          LEFT JOIN wa.identity actor_identity ON actor_identity.id=audit.actor_identity_id
          LEFT JOIN wa.service_credential credential ON credential.id=audit.credential_id
          LEFT JOIN wa.command_receipt command ON command.command_id=audit.resource_id AND command.identity_id=audit.actor_identity_id
          LEFT JOIN wa.integration_inbox source ON source.id=command.inbox_event_id
          LEFT JOIN wa.integration_inbox direct ON direct.id::text=audit.correlation_id AND direct.identity_id=audit.actor_identity_id
          CROSS JOIN scope s
          WHERE audit.created_at<=s.cutoff AND (credential.id IS NULL OR credential.environment=s.environment)
            AND (source.instance_id=s.instance_id OR direct.instance_id=s.instance_id OR credential.allowed_instance_id=s.instance_id
              OR source.id IS NULL AND direct.id IS NULL AND credential.allowed_instance_id IS NULL
                AND (person.id IS NOT NULL OR credential.environment=s.environment
                  OR actor_identity.test_cohort_id IS NULL AND credential.id IS NULL)
              OR audit.actor_identity_id IS NULL AND audit.credential_id IS NULL)
          UNION ALL
          SELECT '06',d.run_id,d.fact_at,'HISTORY_GRAPH_PENDING'
          FROM days d JOIN people driver ON driver.id=d.driver_id
          WHERE d.run_id IS NOT NULL AND d.run_history_erased IS NULL AND (d.origin_preserved OR NOT d.conflicting_origin)
          UNION ALL
          SELECT '07',d.id,d.fact_at,CASE WHEN d.terminal_at_known THEN 'HISTORY_GRAPH_PENDING' ELSE 'MISSING_TERMINAL_AT' END
          FROM days d JOIN people driver ON driver.id=d.driver_id WHERE d.history_erased_at IS NULL AND (d.origin_preserved OR NOT d.conflicting_origin)
        )
        """;
}
