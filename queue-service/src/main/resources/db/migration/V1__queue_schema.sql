-- One row per department. Locked with SELECT ... FOR UPDATE by every queue
-- mutation, so changes to a department are serialised across all instances.
-- state_version lets each instance detect that its in-memory copy is stale.
CREATE TABLE queue_departments
(
    department_id      VARCHAR(32) NOT NULL PRIMARY KEY,
    state_version      BIGINT      NOT NULL,
    pending_request_id VARCHAR(36) NULL,
    updated_at         DATETIME(6) NOT NULL
);

CREATE TABLE queue_entries
(
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    patient_id     VARCHAR(36)  NOT NULL,
    patient_name   VARCHAR(255) NOT NULL,
    department_id  VARCHAR(32)  NOT NULL,
    token_number   VARCHAR(40)  NOT NULL,
    token_sequence INT          NOT NULL,
    checkin_date   DATE         NOT NULL,
    priority       VARCHAR(16)  NOT NULL,
    access_code    VARCHAR(64)  NOT NULL,
    status         VARCHAR(24)  NOT NULL,
    doctor_id      VARCHAR(36)  NULL,
    doctor_name    VARCHAR(255) NULL,
    room_number    VARCHAR(32)  NULL,
    joined_at      DATETIME(6)  NOT NULL,
    called_at      DATETIME(6)  NULL,
    assigned_at    DATETIME(6)  NULL,
    completed_at   DATETIME(6)  NULL,
    version        BIGINT       NOT NULL,
    -- One queue entry per check-in: makes redelivered patient.checked-in events harmless
    CONSTRAINT uk_queue_entries_patient UNIQUE (patient_id),
    INDEX idx_queue_entries_dept_status (department_id, status)
);

-- Transactional outbox: events are written in the same transaction as the
-- state change and published to Kafka afterwards by OutboxRelay.
CREATE TABLE outbox_events
(
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id     VARCHAR(36)   NOT NULL,
    topic        VARCHAR(128)  NOT NULL,
    message_key  VARCHAR(128)  NOT NULL,
    payload      LONGTEXT      NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    published_at DATETIME(6)   NULL,
    attempts     INT           NOT NULL,
    last_error   VARCHAR(1000) NULL,
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id),
    INDEX idx_outbox_unpublished (published_at, id)
);
