-- The department row doubles as the token counter. Check-in locks it with
-- SELECT ... FOR UPDATE, so concurrent check-ins are serialised per department
-- (and across every patient-service instance) while departments proceed in parallel.
CREATE TABLE departments
(
    dept_id           VARCHAR(32)  NOT NULL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    token_date        DATE         NULL,
    last_token_number INT          NOT NULL
);

CREATE TABLE patients
(
    id                   VARCHAR(36)  NOT NULL PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL,
    phone                VARCHAR(10)  NOT NULL,
    department           VARCHAR(32)  NOT NULL,
    priority             VARCHAR(16)  NOT NULL,
    status               VARCHAR(24)  NOT NULL,
    token_number         VARCHAR(40)  NOT NULL,
    token_sequence       INT          NOT NULL,
    checkin_date         DATE         NOT NULL,
    checkin_time         DATETIME(6)  NOT NULL,
    access_code          VARCHAR(64)  NOT NULL,
    assigned_doctor_name VARCHAR(255) NULL,
    room_number          VARCHAR(32)  NULL,
    version              BIGINT       NOT NULL,
    -- Safety net under the row lock: a token can never be issued twice
    CONSTRAINT uk_patients_token UNIQUE (department, checkin_date, token_sequence),
    CONSTRAINT uk_patients_access_code UNIQUE (access_code),
    CONSTRAINT fk_patients_department FOREIGN KEY (department) REFERENCES departments (dept_id),
    INDEX idx_patients_dept_date (department, checkin_date)
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
