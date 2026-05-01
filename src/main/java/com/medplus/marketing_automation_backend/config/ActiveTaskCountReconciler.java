package com.medplus.marketing_automation_backend.config;

import com.medplus.marketing_automation_backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Realigns {@code users.current_active_tasks} with the actual number of
 * non-terminal rows in {@code work_tasks} on every startup.
 *
 * <p>The counter is incremented by {@code RoutingEngineService} on assignment
 * and decremented by {@code ApprovalService} when QC approves / rejects a task,
 * but it can drift in a handful of edge paths — manual reassignment, partial
 * seeder runs, schema upgrades, deleted campaigns, force-assigns, etc.
 *
 * <p>Reconciling on boot guarantees the routing engine, capacity dashboard, and
 * time-tracking report all start the day from a consistent source of truth.
 */
@Slf4j
@Component
@Order(4)
public class ActiveTaskCountReconciler implements CommandLineRunner {

    private final UserRepository userRepo;

    public ActiveTaskCountReconciler(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public void run(String... args) {
        int updated = userRepo.reconcileActiveTaskCounters();
        log.info("ActiveTaskCountReconciler: realigned current_active_tasks for {} user row(s).", updated);
    }
}
