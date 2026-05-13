package com.medplus.marketing_automation_backend.enums;

public enum TaskStatus {
    ASSIGNED("Assigned"),
    ACCEPTED("Accepted"),
    IN_PROGRESS("In Progress"),
    QC_REVIEW("QC Review"),
    REWORK("Rework"),
    COMPLETED("Completed"),
    /**
     * Task was explicitly rejected by a QC reviewer. Unlike CANCELLED (which
     * covers sibling tasks swept away when a campaign is closed), REJECTED
     * means this specific task failed QC review and caused the campaign to be
     * closed.
     */
    REJECTED("Rejected"),
    /**
     * Task was cancelled — either its parent campaign was rejected after
     * partial routing, or a sibling task was rejected at QC causing all
     * remaining open tasks to be swept. The work is not "completed" (no asset
     * was approved), but the task is closed and should no longer count toward
     * the assignee's capacity or appear as actionable in their queue.
     */
    CANCELLED("Cancelled"),
    /**
     * Task was held by a manager — temporarily removed from a worker's queue
     * to free capacity for a higher-priority campaign that's waiting at the
     * marketing-head approval gate. A held task does NOT count toward the
     * (former) assignee's workload, is hidden from their queue, and lives in
     * the dedicated "Held Tasks" tab where the manager can unhold it back
     * into the auto-routing engine. Only ASSIGNED tasks can be held — once a
     * worker has clicked Start (IN_PROGRESS) or beyond, the task can no
     * longer be ripped out from under them.
     */
    HELD("On Hold");

    private final String label;
    TaskStatus(String label) { this.label = label; }
    public String getLabel() { return label; }

    /** Statuses that no longer count toward a user's active workload. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == REJECTED;
    }

    /** True for statuses that consume one of the assignee's workload slots. */
    public boolean consumesCapacity() {
        return this == ASSIGNED || this == ACCEPTED
            || this == IN_PROGRESS || this == REWORK
            || this == QC_REVIEW;
    }
}
