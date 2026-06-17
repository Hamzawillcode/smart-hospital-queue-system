INSERT INTO departments (dept_id, name, current_queue_size)
VALUES ('OPD', 'Outpatient Department', 0)
ON DUPLICATE KEY UPDATE dept_id=dept_id;

INSERT INTO departments (dept_id, name, current_queue_size)
VALUES ('EMERGENCY', 'Emergency', 0)
ON DUPLICATE KEY UPDATE dept_id=dept_id;

INSERT INTO departments (dept_id, name, current_queue_size)
VALUES ('CARDIOLOGY', 'Cardiology', 0)
ON DUPLICATE KEY UPDATE dept_id=dept_id;