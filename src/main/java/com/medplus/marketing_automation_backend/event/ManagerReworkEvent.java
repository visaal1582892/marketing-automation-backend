package com.medplus.marketing_automation_backend.event;

/**
 * Published when a manager sends a task back for rework (NEEDS_REWORK decision).
 *
 * @param taskId         the WORK-TASK-xxx identifier
 * @param managerName    display name of the manager
 * @param assignedUserId the worker who must redo the task
 */
public record ManagerReworkEvent(String taskId, String managerName, int assignedUserId) {}
