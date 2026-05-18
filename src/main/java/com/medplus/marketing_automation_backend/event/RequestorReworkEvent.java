package com.medplus.marketing_automation_backend.event;

/**
 * Published when a requestor sends a task back for rework.
 *
 * @param taskId         the WORK-TASK-xxx identifier
 * @param requestorName  display name of the requestor
 * @param assignedUserId the worker who must redo the task
 */
public record RequestorReworkEvent(String taskId, String requestorName, int assignedUserId) {}
