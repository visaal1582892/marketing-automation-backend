package com.medplus.marketing_automation_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Snapshot of whether a campaign can be auto-routed right now.
 * Built by {@code RoutingEngineService.capacityReport(campaignId)}.
 * Used by the manager's capacity dashboard and by the Hold/Unhold workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapacityReport {

    /** False when at least one un-routed deliverable has no available user in its role. */
    private boolean canRoute;

    /** Total deliverables on the campaign that still need a worker. */
    private int unroutedDeliverables;

    /** Per-role breakdown — one entry per role that participates in this campaign. */
    private List<RoleCapacity> roles;

    /**
     * Capacity status for one role within a campaign.
     *
     * <p>{@code requiredSlots} is the number of un-routed deliverables that
     * map to this role. {@code availableSlots} is the sum of free slots
     * (capacity minus current active tasks) across active users in the role.
     * The role is "blocked" when required exceeds available.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleCapacity {
        private String roleId;
        private String roleName;
        private int    requiredSlots;
        private int    availableSlots;
        private boolean blocked;
        /** All active users in the role with their workload + held-able tasks. */
        private List<UserCapacity> users;
    }

    /**
     * One user's workload inside a {@link RoleCapacity}. The {@code openTasks}
     * field carries the user's currently-ASSIGNED tasks — the manager can
     * pick any of them to "Hold" from the busy-team modal, freeing a slot
     * for the campaign waiting at the approval gate.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserCapacity {
        private Integer userId;
        private String  fullName;
        private String  roleId;
        private String  roleName;
        private Integer currentActiveTasks;
        /** ASSIGNED tasks the manager can hold (IN_PROGRESS work is excluded). */
        private List<WorkTaskResponse> openTasks;
    }
}
