package com.medplus.marketing_automation_backend.event;

/**
 * Published when a work task is assigned (auto-routed or manually) to a user.
 *
 * @param taskId         the WORK-TASK-xxx identifier
 * @param assignedUserId the user receiving the task
 * @param taskName       display name of the granular task (may be null)
 */
public record TaskAssignedEvent(String taskId, int assignedUserId, String taskName) {}
