package com.medplus.marketing_automation_backend.event;

/**
 * Published when a manager approves a task during QC review.
 * Two recipients are notified: the worker (default template) and the
 * campaign requestor (Requestor role-specific template).
 *
 * @param taskId       the approved task
 * @param managerName  full name of the approving manager
 * @param workerId     user ID of the task's assigned worker
 * @param requestorId  user ID of the campaign requestor
 */
public record ManagerQcApprovedEvent(
        String taskId,
        String managerName,
        int    workerId,
        int    requestorId) {}
