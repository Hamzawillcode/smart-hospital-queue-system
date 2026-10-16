#!/bin/bash
# Database-per-service on one MySQL server: each service gets its own schema and
# its own user, and can only touch its own schema.
#
# Runs automatically in the MySQL container on first start. For a local MySQL:
#   MYSQL_ROOT_PASSWORD=... PATIENT_DB_PASSWORD=... QUEUE_DB_PASSWORD=... DOCTOR_DB_PASSWORD=... \
#     bash infra/mysql/init/01-create-databases.sh
set -euo pipefail

: "${MYSQL_ROOT_PASSWORD:?}" "${PATIENT_DB_PASSWORD:?}" "${QUEUE_DB_PASSWORD:?}" "${DOCTOR_DB_PASSWORD:?}"

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS opd_patient;
CREATE DATABASE IF NOT EXISTS opd_queue;
CREATE DATABASE IF NOT EXISTS opd_doctor;

CREATE USER IF NOT EXISTS 'patient_svc'@'%' IDENTIFIED BY '${PATIENT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'queue_svc'@'%' IDENTIFIED BY '${QUEUE_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'doctor_svc'@'%' IDENTIFIED BY '${DOCTOR_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON opd_patient.* TO 'patient_svc'@'%';
GRANT ALL PRIVILEGES ON opd_queue.* TO 'queue_svc'@'%';
GRANT ALL PRIVILEGES ON opd_doctor.* TO 'doctor_svc'@'%';
FLUSH PRIVILEGES;
SQL
