INSERT INTO doctors (doctor_id, name, department_id,
                     specialization, room_number, status, version)
VALUES
    (UUID(), 'Dr. Sharma', 'OPD',
     'General Medicine', 'OPD-1', 'AVAILABLE', 0),
    (UUID(), 'Dr. Khan', 'OPD',
     'General Medicine', 'OPD-2', 'AVAILABLE', 0),
    (UUID(), 'Dr. Mehta', 'EMERGENCY',
     'Emergency Medicine', 'EM-1', 'AVAILABLE', 0),
    (UUID(), 'Dr. Singh', 'CARDIOLOGY',
     'Cardiology', 'CARD-1', 'AVAILABLE', 0);