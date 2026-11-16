package com.vanrides.vanrides.whatsapp.privacy;

/** Every row has a cohort owner. Unowned pre-authentication namespaces are never inferred. */
final class WaPrivacyTechnicalQueries {
    private WaPrivacyTechnicalQueries() { }
    static final String CONTEXT = "c.context_json-ARRAY['passengerDraft','driverDraft','runDraft','attendanceDraft','locationDraft','conversationOwner']";
    static final String CANDIDATES = """
        WITH scope AS (SELECT ?::text environment,?::text instance,?::boolean synthetic_only,?::timestamptz as_of),
        people AS (
          SELECT i.id,EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=i.id AND a.instance_id IS DISTINCT FROM scope.instance) ambiguous
          FROM wa.identity i JOIN wa.test_cohort cohort ON cohort.id=i.test_cohort_id CROSS JOIN scope
          WHERE cohort.environment=scope.environment AND (NOT scope.synthetic_only OR cohort.synthetic)
            AND EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=i.id AND a.instance_id=scope.instance)),
        keys AS (
          SELECT d.jkt,d.driver_id FROM wa.driver_device d CROSS JOIN scope WHERE d.environment=scope.environment
          UNION SELECT p.jkt,p.driver_id FROM wa.pairing_challenge p CROSS JOIN scope
            WHERE p.environment=scope.environment AND p.driver_id IS NOT NULL),
        replay_owner AS (
          SELECT r.id,d.driver_id FROM wa.operation_replay r JOIN wa.driver_device d ON d.id=r.scope_id CROSS JOIN scope
            WHERE d.environment=scope.environment AND r.operation_kind IN ('SESSION_REVOKE','TRACK_ACTIVATE','TRACK_RESUME','TRACK_STATE')
          UNION SELECT r.id,d.driver_id FROM wa.operation_replay r JOIN wa.device_token t ON t.id=r.scope_id
            JOIN wa.driver_device d ON d.id=t.device_id CROSS JOIN scope WHERE r.operation_kind='REFRESH' AND d.environment=scope.environment
          UNION SELECT r.id,p.driver_id FROM wa.operation_replay r JOIN wa.pairing_challenge p ON p.id=r.scope_id CROSS JOIN scope
            WHERE r.operation_kind='PAIR_REDEEM' AND p.environment=scope.environment AND p.driver_id IS NOT NULL
          UNION SELECT r.id,k.driver_id FROM wa.operation_replay r CROSS JOIN scope JOIN keys k ON true
            WHERE r.operation_kind='PAIR_BEGIN' AND r.scope_id=substr(encode(sha256(convert_to(scope.environment,'UTF8')||decode('00','hex')||convert_to(k.jkt,'UTF8')),'hex'),1,32)::uuid),
        raw AS (
          SELECT 'SAMPLE' family,s.id,s.session_id parent,t.run_id,p.id owner,s.received_at fact_at,p.ambiguous,
            NULL::text reason FROM wa.tracking_sample_receipt s JOIN wa.tracking_session t ON t.id=s.session_id
            JOIN wa.operation_run r ON r.id=t.run_id JOIN wa.driver_device d ON d.id=t.device_id
            JOIN people p ON p.id=t.driver_id CROSS JOIN scope
            WHERE d.environment=scope.environment AND t.state IN ('STOPPED','EXPIRED','REVOKED') AND r.state IN ('COMPLETED','CANCELED')
              AND s.received_at<=scope.as_of-INTERVAL '7 days'
          UNION ALL SELECT 'BATCH',b.batch_id,b.session_id,t.run_id,p.id,b.received_at,p.ambiguous,
            CASE WHEN EXISTS(SELECT 1 FROM wa.tracking_sample_receipt s WHERE s.session_id=b.session_id AND s.batch_id=b.batch_id) THEN 'CHILDREN_PENDING' END
            FROM wa.tracking_batch_receipt b JOIN wa.tracking_session t ON t.id=b.session_id
            JOIN wa.operation_run r ON r.id=t.run_id JOIN wa.driver_device d ON d.id=t.device_id JOIN people p ON p.id=t.driver_id CROSS JOIN scope
            WHERE d.environment=scope.environment AND t.state IN ('STOPPED','EXPIRED','REVOKED') AND r.state IN ('COMPLETED','CANCELED')
              AND b.received_at<=scope.as_of-INTERVAL '7 days'
          UNION ALL SELECT 'CONTEXT',c.identity_id,NULL::uuid,NULL::uuid,p.id,h.last_human,p.ambiguous,
            CASE WHEN wa.privacy_has_active_reference(p.id) THEN 'ACTIVE_REFERENCE'
              WHEN c.lease_expires_at>scope.as_of OR EXISTS(SELECT 1 FROM wa.integration_inbox i WHERE i.identity_id=p.id
                AND i.state='CLAIMED' AND i.lease_expires_at>scope.as_of) THEN 'HUMAN_WORK_BUSY' END
            FROM wa.conversation_state c JOIN people p ON p.id=c.identity_id CROSS JOIN scope
            JOIN LATERAL(SELECT max(i.created_at) last_human FROM wa.integration_inbox i
              WHERE i.identity_id=p.id AND i.instance_id=scope.instance AND i.category='MESSAGE') h ON true
            WHERE h.last_human<=scope.as_of-INTERVAL '7 days' AND c.context_json<>
        """ + CONTEXT + """
          UNION ALL SELECT 'REPLAY',r.id,NULL::uuid,NULL::uuid,p.id,r.expires_at,p.ambiguous,
            CASE WHEN wa.privacy_has_active_reference(p.id) THEN 'ACTIVE_REFERENCE'
              WHEN (SELECT count(DISTINCT o.driver_id) FROM replay_owner o WHERE o.id=r.id)>1 THEN 'AMBIGUOUS_OWNER' END
            FROM wa.operation_replay r JOIN replay_owner o ON o.id=r.id JOIN people p ON p.id=o.driver_id CROSS JOIN scope
            WHERE r.expires_at<=scope.as_of AND r.response_ciphertext<>decode('00','hex')
          UNION ALL SELECT 'DEVICE',d.id,NULL::uuid,NULL::uuid,p.id,d.revoked_at,p.ambiguous,NULL::text
            FROM wa.driver_device d JOIN people p ON p.id=d.driver_id CROSS JOIN scope
            WHERE d.environment=scope.environment AND d.status='REVOKED' AND d.public_jwk<>'{}'
              AND NOT wa.privacy_has_active_reference(p.id)
              AND NOT EXISTS(SELECT 1 FROM wa.device_token token WHERE token.device_id=d.id AND token.expires_at>scope.as_of)
          UNION ALL SELECT 'PAIRING',c.id,NULL::uuid,NULL::uuid,p.id,c.expires_at,p.ambiguous,
            CASE WHEN wa.privacy_has_active_reference(p.id) THEN 'ACTIVE_REFERENCE'
              WHEN EXISTS(SELECT 1 FROM wa.operation_replay r JOIN replay_owner o ON o.id=r.id WHERE o.driver_id=p.id
                AND r.response_ciphertext<>decode('00','hex')) THEN 'REPLAY_PENDING' END
            FROM wa.pairing_challenge c JOIN people p ON p.id=c.driver_id CROSS JOIN scope
            WHERE c.environment=scope.environment AND c.expires_at<=scope.as_of
              AND c.state IN ('APPROVED','REDEEMED','REVOKED','EXPIRED')
          UNION ALL SELECT 'CIPHER',o.id,NULL::uuid,NULL::uuid,p.id,o.expires_at,p.ambiguous,
            CASE WHEN EXISTS(SELECT 1 FROM wa.message_attempt a WHERE a.outbox_id=o.id AND a.state='SUBMITTING') THEN 'SUBMITTING' END
            FROM wa.message_outbox o JOIN people p ON p.id=o.recipient_identity_id CROSS JOIN scope
            WHERE (o.private_payload_ciphertext IS NOT NULL OR o.start_payload_ciphertext IS NOT NULL) AND o.expires_at<=scope.as_of
              AND EXISTS(SELECT 1 FROM wa.command_receipt r JOIN wa.integration_inbox i ON i.id=r.inbox_event_id
                WHERE r.command_id=o.command_id AND i.instance_id=scope.instance)
        ), eligible AS (
          SELECT DISTINCT family,id,parent,run_id,owner,fact_at,CASE WHEN ambiguous THEN 'MULTI_INSTANCE' ELSE reason END reason,
            (CASE family WHEN 'SAMPLE' THEN '01' WHEN 'BATCH' THEN '02' WHEN 'CONTEXT' THEN '03' WHEN 'REPLAY' THEN '04'
              WHEN 'CIPHER' THEN '05' WHEN 'PAIRING' THEN '06' ELSE '07' END)||':'||
            to_char(fact_at AT TIME ZONE 'UTC','YYYYMMDDHH24MISSUS')||':'||id||':'||COALESCE(parent,owner) work_key FROM raw)
        """;
}
