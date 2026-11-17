-- A passenger authorizes each routine and pickup separately. Existing run snapshots stay immutable.
DROP INDEX wa.uk_pickup_current_approved;

ALTER TABLE wa.passenger_link_routine ADD COLUMN pickup_point_id UUID;
ALTER TABLE wa.passenger_link_routine ADD CONSTRAINT fk_selection_pickup_owners
    FOREIGN KEY(pickup_point_id,link_id,driver_id,passenger_id)
    REFERENCES wa.pickup_point(id,link_id,driver_id,passenger_id);

-- Prefer the admitted point only when there is no pending legacy correction.
UPDATE wa.passenger_link_routine s SET pickup_point_id=m.pickup_point_id
FROM wa.routine_member m JOIN wa.pickup_point p ON p.id=m.pickup_point_id AND p.state='APPROVED'
WHERE m.link_id=s.link_id AND m.routine_id=s.routine_id AND m.passenger_id=s.passenger_id
AND NOT EXISTS(SELECT 1 FROM wa.pickup_point p WHERE p.link_id=s.link_id AND p.state='PROPOSED' AND p.valid_until IS NULL);

-- Scope a unique legacy proposal to each selection, including corrections of an admitted pickup.
-- Original points, approvals, versions and run snapshots remain unchanged.
WITH scoped AS MATERIALIZED (
    SELECT s.id AS selection_id,gen_random_uuid() AS new_id,p.*
    FROM wa.passenger_link_routine s JOIN wa.pickup_point p ON p.link_id=s.link_id
    WHERE p.state='PROPOSED' AND p.valid_until IS NULL AND p.privacy_erased_at IS NULL
    AND (SELECT count(*) FROM wa.pickup_point other WHERE other.link_id=s.link_id
        AND other.state='PROPOSED' AND other.valid_until IS NULL)=1
), copied AS (
    INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,label,reference_text,
        source_inbox_event_id,common_pickup_point_id,common_pickup_version,created_at,valid_from)
    SELECT new_id,link_id,driver_id,passenger_id,latitude,longitude,label,reference_text,
        source_inbox_event_id,common_pickup_point_id,common_pickup_version,created_at,valid_from FROM scoped
    RETURNING id
)
UPDATE wa.passenger_link_routine s SET pickup_point_id=scoped.new_id
FROM scoped JOIN copied ON copied.id=scoped.new_id WHERE s.id=scoped.selection_id;
CREATE INDEX ix_selection_pickup ON wa.passenger_link_routine(pickup_point_id);

ALTER TABLE wa.passenger_response_route DROP CONSTRAINT ck_passenger_route_event_state;
ALTER TABLE wa.passenger_response_route ADD CONSTRAINT ck_passenger_route_event_state CHECK (
    event_kind='ACTOR'
    OR (event_kind='REQUESTED' AND link_state IN ('REQUESTED','APPROVED') AND pickup_state='PROPOSED' AND pickup_id IS NOT NULL)
    OR (event_kind='POINT_CHANGE' AND link_state IN ('REQUESTED','APPROVED') AND pickup_state IN ('PROPOSED','APPROVED') AND pickup_id IS NOT NULL)
    OR (event_kind IN ('APPROVED','POINT_APPROVED') AND link_state='APPROVED' AND pickup_state='APPROVED' AND pickup_id IS NOT NULL)
    OR (event_kind='REJECTED' AND link_state='REJECTED')
    OR (event_kind='RECTIFY' AND link_state IN ('REQUESTED','APPROVED'))
    OR (event_kind='CLOSED' AND link_state='CLOSED')
);
