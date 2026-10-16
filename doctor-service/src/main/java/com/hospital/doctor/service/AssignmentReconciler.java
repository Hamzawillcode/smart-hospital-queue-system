package com.hospital.doctor.service;

import com.hospital.doctor.model.DoctorRequest;
import com.hospital.doctor.repository.DoctorRequestRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net for a lost wake-up.
 *
 * SKIP LOCKED means a matcher never waits: if transaction A frees a doctor while
 * transaction B inserts a request, each can skip the other's still-locked row and
 * both commit with an available doctor and a pending request unmatched. Rather than
 * fall back to blocking locks (and risk deadlocks), we re-run matching every
 * couple of seconds for departments that still have pending requests.
 */
@Component
@ConditionalOnProperty(name = "assignment.reconciler.enabled", havingValue = "true", matchIfMissing = true)
public class AssignmentReconciler {

    private static final Logger log = LogManager.getLogger(AssignmentReconciler.class);

    private final DoctorRequestRepository requests;
    private final AssignmentService assignmentService;

    public AssignmentReconciler(DoctorRequestRepository requests, AssignmentService assignmentService) {
        this.requests = requests;
        this.assignmentService = assignmentService;
    }

    @Scheduled(fixedDelayString = "${assignment.reconcile-interval-ms:2000}")
    public void reconcile() {
        for (String departmentId : requests.findDepartmentsWithStatus(DoctorRequest.Status.PENDING)) {
            try {
                int assigned = assignmentService.matchDepartment(departmentId);
                if (assigned > 0) {
                    log.info("Reconciler assigned {} pending request(s) in {}", assigned, departmentId);
                }
            } catch (RuntimeException e) {
                log.warn("Reconciliation failed for {}: {}", departmentId, e.toString());
            }
        }
    }
}
