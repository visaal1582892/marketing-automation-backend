package com.medplus.marketing_automation_backend.event;

/**
 * Published when a manager holds a task (pulls it from the assignee's queue).
 *
 * @param taskId         the WORK-TASK-xxx identifier
 * @param managerName    display name of the manager who held the task
 * @param assignedUserId the worker who was holding the task before the hold
 */
public record TaskHeldByManagerEvent(String taskId, String managerName, int assignedUserId) {}
