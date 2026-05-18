package com.medplus.marketing_automation_backend.event;

/**
 * Published when a manager rejects a task during QC review.
 *
 * @param taskId         the WORK-TASK-xxx identifier
 * @param managerName    display name of the manager
 * @param assignedUserId the worker whose task was rejected
 */
public record ManagerRejectEvent(String taskId, String managerName, int assignedUserId) {}
