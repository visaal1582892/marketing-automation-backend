package com.medplus.marketing_automation_backend.exception;

import com.medplus.marketing_automation_backend.dto.CapacityReport;
import lombok.Getter;

/**
 * Thrown when an action depends on auto-routing but the relevant team has no
 * available capacity. Carries a {@link CapacityReport} so the controller can
 * expose the busy-team breakdown directly to the UI — the marketing head sees
 * exactly which users are full and can pick tasks to "Hold" before retrying.
 *
 * <p>Mapped to HTTP 409 Conflict by {@code GlobalExceptionHandler}.
 */
@Getter
public class InsufficientCapacityException extends RuntimeException {

    private final CapacityReport report;

    public InsufficientCapacityException(String message, CapacityReport report) {
        super(message);
        this.report = report;
    }
}
