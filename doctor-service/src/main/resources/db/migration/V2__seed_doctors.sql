-- Development seed data (fixed ids so they are stable across environments).
INSERT INTO doctors (doctor_id, name, department_id, specialization, room_number, status, version)
VALUES ('6f1c2a10-0000-4000-8000-000000000001', 'Dr. Sharma', 'OPD', 'General Medicine', 'OPD-1', 'AVAILABLE', 0),
       ('6f1c2a10-0000-4000-8000-000000000002', 'Dr. Khan', 'OPD', 'General Medicine', 'OPD-2', 'AVAILABLE', 0),
       ('6f1c2a10-0000-4000-8000-000000000003', 'Dr. Mehta', 'EMERGENCY', 'Emergency Medicine', 'EM-1', 'AVAILABLE', 0),
       ('6f1c2a10-0000-4000-8000-000000000004', 'Dr. Singh', 'CARDIOLOGY', 'Cardiology', 'CARD-1', 'AVAILABLE', 0);
