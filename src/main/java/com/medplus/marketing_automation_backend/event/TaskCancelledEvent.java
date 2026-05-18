package com.medplus.marketing_automation_backend.event;

/**
 * Published when a task is cancelled by a manager.
 *
 * @param taskId         the WORK-TASK-xxx identifier
 * @param managerName    display name of the manager who cancelled
 * @param assignedUserId the worker who was assigned the task (may be 0 if unassigned)
 */
public record TaskCancelledEvent(String taskId, String managerName, int assignedUserId) {}
