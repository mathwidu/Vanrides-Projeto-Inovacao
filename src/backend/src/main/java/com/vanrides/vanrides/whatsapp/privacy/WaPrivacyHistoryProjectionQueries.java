package com.vanrides.vanrides.whatsapp.privacy;

final class WaPrivacyHistoryProjectionQueries {
    private WaPrivacyHistoryProjectionQueries() { }
    static final String WORK = """
        WITH scope AS (SELECT ?::text environment,?::text instance_id,?::boolean synthetic_only,?::timestamptz as_of),
        origins AS (
          SELECT o.*,r.id run_id,r.state run_state,r.completed_at,r.snapshot_at,r.created_at run_created,
            r.history_erased_at run_erased,wa.privacy_history_run_snapshot(r.snapshot_json,r.snapshot_at) minimal,
            source.kind source_kind,source.origin_id source_id,
            EXISTS(SELECT 1 FROM wa.privacy_history_sources(o.id) v WHERE v.environment IS DISTINCT FROM s.environment
              OR v.instance_id IS DISTINCT FROM s.instance_id) conflict,
            wa.privacy_history_subject_scope(o.driver_id,s.environment,s.instance_id) driver_scope,
            NOT EXISTS(SELECT 1 FROM wa.run_stop stop LEFT JOIN wa.identity passenger ON passenger.id=stop.passenger_id
              LEFT JOIN wa.test_cohort pc ON pc.id=passenger.test_cohort_id WHERE stop.run_id=r.id AND
              (NOT wa.privacy_history_subject_scope(stop.passenger_id,s.environment,s.instance_id) OR s.synthetic_only AND NOT pc.synthetic)) passengers_scope
          FROM wa.routine_occurrence o JOIN wa.identity i ON i.id=o.driver_id JOIN wa.test_cohort c ON c.id=i.test_cohort_id
          CROSS JOIN scope s LEFT JOIN wa.operation_run r ON r.occurrence_id=o.id
          LEFT JOIN LATERAL (SELECT * FROM wa.privacy_history_sources(o.id) v
            WHERE v.environment=s.environment AND v.instance_id=s.instance_id AND v.origin_id IS NOT NULL ORDER BY v.kind,v.origin_id LIMIT 1) source ON true
          WHERE c.environment=s.environment AND (NOT s.synthetic_only OR c.synthetic)
            AND greatest(o.scheduled_at,o.created_at)<=s.as_of-INTERVAL '90 days'
            AND (o.history_environment=s.environment AND o.history_instance_id=s.instance_id
              OR o.history_origin_bound_at IS NULL AND (source.origin_id IS NOT NULL OR
                NOT EXISTS(SELECT 1 FROM wa.privacy_history_sources(o.id)) OR
                EXISTS(SELECT 1 FROM wa.privacy_history_sources(o.id) unresolved_source
                  WHERE (unresolved_source.environment IS NULL OR unresolved_source.environment=s.environment)
                    AND (unresolved_source.instance_id IS NULL OR unresolved_source.instance_id=s.instance_id)
                    AND (unresolved_source.environment IS NULL OR unresolved_source.instance_id IS NULL))))
        ), days AS (
          SELECT o.*,CASE WHEN conflict THEN 'ORIGIN_CONFLICT' WHEN NOT driver_scope OR NOT passengers_scope THEN 'SUBJECT_OUT_OF_SCOPE'
            WHEN run_id IS NOT NULL AND run_state NOT IN ('COMPLETED','CANCELED') THEN 'ACTIVE_REFERENCE'
            WHEN run_id IS NOT NULL AND completed_at IS NULL THEN 'MISSING_TERMINAL_AT'
            WHEN run_id IS NOT NULL AND minimal IS NULL THEN 'SNAPSHOT_DIGEST_MISSING'
            WHEN EXISTS(SELECT 1 FROM wa.start_intent i CROSS JOIN scope s WHERE i.occurrence_id=o.id
              AND i.state IN ('PENDING','ACTIVATED') AND i.expires_at>s.as_of)
              OR EXISTS(SELECT 1 FROM wa.tracking_session t WHERE t.run_id=o.run_id AND t.state IN ('STARTING','ACTIVE','DEGRADED','STOP_REQUESTED'))
              THEN 'ACTIVE_REFERENCE' END reason
          FROM origins o CROSS JOIN scope s
          WHERE o.run_id IS NULL OR o.completed_at IS NULL OR o.run_state NOT IN ('COMPLETED','CANCELED')
            OR greatest(o.completed_at,o.scheduled_at,o.snapshot_at,o.run_created)<=s.as_of-INTERVAL '90 days'
        ), work AS (
          SELECT '00' family,o.id occurrence_id,'00000000-0000-0000-0000-000000000000'::uuid parent_id,o.id id,
            CASE WHEN o.reason IS NOT NULL THEN o.reason WHEN o.source_id IS NULL THEN 'ORIGIN_MISSING' END reason
          FROM days o WHERE o.history_origin_bound_at IS NULL
          UNION ALL SELECT '01',o.id,o.run_id,stop.id,o.reason FROM days o JOIN wa.run_stop stop ON stop.run_id=o.run_id
            WHERE o.history_origin_bound_at IS NOT NULL AND stop.history_erased_at IS NULL
          UNION ALL SELECT '02',o.id,o.run_id,p.physical_stop_key,o.reason FROM days o JOIN wa.approach_progress p ON p.run_id=o.run_id
            WHERE o.history_origin_bound_at IS NOT NULL
          UNION ALL SELECT '03',o.id,o.run_id,p.run_id,o.reason FROM days o JOIN wa.run_finish_progress p ON p.run_id=o.run_id
            WHERE o.history_origin_bound_at IS NOT NULL
          UNION ALL SELECT '04',o.id,o.id,o.run_id,o.reason FROM days o WHERE o.history_origin_bound_at IS NOT NULL
            AND o.run_id IS NOT NULL AND o.run_erased IS NULL
          UNION ALL SELECT '05',o.id,'00000000-0000-0000-0000-000000000000'::uuid,o.id,
            CASE WHEN o.reason IS NOT NULL THEN o.reason
              WHEN EXISTS(SELECT 1 FROM wa.attendance a WHERE a.routine_id=o.routine_id AND a.local_date=o.local_date)
                OR EXISTS(SELECT 1 FROM wa.attendance_question q WHERE q.occurrence_id=o.id)
                OR EXISTS(SELECT 1 FROM wa.occurrence_roster_override v WHERE v.occurrence_id=o.id) THEN 'HISTORY_LEAVES_PENDING'
              WHEN NOT wa.privacy_history_occurrence_due(o.id,s.as_of) THEN 'GRAPH_COPY_PENDING' END
          FROM days o CROSS JOIN scope s WHERE o.history_origin_bound_at IS NOT NULL AND o.history_erased_at IS NULL
        )
        """;
}
