package com.hospital.queue.manager;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * In-memory queue engine.
 *
 * <ul>
 *   <li>One {@link ReentrantLock} per department, held in a {@link ConcurrentHashMap}.
 *       Writers for OPD and EMERGENCY never block each other: there is no global lock.</li>
 *   <li>Each department's state is an immutable {@link DepartmentSnapshot} behind a
 *       volatile reference. Writers build a new snapshot and swap it in; readers just read
 *       the reference, lock-free.</li>
 *   <li>A snapshot is installed only after the database transaction that produced it has
 *       committed, so memory can never contain a change the database rolled back.</li>
 * </ul>
 *
 * Department keys come only from departments that exist in the database, so the maps
 * stay bounded by the number of departments.
 */
@Component
public class QueueManager {

    private static final class Department {
        private final ReentrantLock lock = new ReentrantLock();
        private volatile DepartmentSnapshot snapshot = DepartmentSnapshot.unloaded();
    }

    private final ConcurrentHashMap<String, Department> departments = new ConcurrentHashMap<>();

    /** Runs {@code work} while holding this department's lock. Other departments are unaffected. */
    public <T> T withDepartmentLock(String departmentId, Supplier<T> work) {
        ReentrantLock lock = department(departmentId).lock;
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    /** Lock-free read of the last committed snapshot. */
    public DepartmentSnapshot snapshot(String departmentId) {
        Department department = departments.get(departmentId);
        return department == null ? DepartmentSnapshot.unloaded() : department.snapshot;
    }

    /** Installs a committed snapshot. Caller must hold the department lock. */
    public void install(String departmentId, DepartmentSnapshot snapshot) {
        Department department = department(departmentId);
        if (!department.lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Department lock not held for " + departmentId);
        }
        department.snapshot = snapshot;
    }

    public Set<String> loadedDepartments() {
        return Set.copyOf(departments.keySet());
    }

    private Department department(String departmentId) {
        return departments.computeIfAbsent(departmentId, id -> new Department());
    }
}
