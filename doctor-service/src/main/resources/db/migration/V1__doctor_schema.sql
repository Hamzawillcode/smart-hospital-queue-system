CREATE TABLE doctors
(
    doctor_id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    department_id      VARCHAR(32)  NOT NULL,
    specialization     VARCHAR(255) NULL,
    room_number        VARCHAR(32)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    current_request_id VARCHAR(36)  NULL,
    last_assigned_at   DATETIME(6)  NULL,
    version            BIGINT       NOT NULL,
    -- serves "first available doctor in department" (least recently assigned first)
    INDEX idx_doctors_dept_status (department_id, status, last_assigned_at)
);

-- One row per doctor.requested; request_id (= queue entry id) is the idempotency key.
CREATE TABLE doctor_requests
(
    request_id    VARCHAR(36) NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36) NOT NULL,
    token_number  VARCHAR(40) NOT NULL,
    department_id VARCHAR(32) NOT NULL,
    priority      VARCHAR(16) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    doctor_id     VARCHAR(36) NULL,
    requested_at  DATETIME(6) NOT NULL,
    assigned_at   DATETIME(6) NULL,
    closed_at     DATETIME(6) NULL,
    version       BIGINT      NOT NULL,
    INDEX idx_requests_dept_status (department_id, status, requested_at)
);

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
