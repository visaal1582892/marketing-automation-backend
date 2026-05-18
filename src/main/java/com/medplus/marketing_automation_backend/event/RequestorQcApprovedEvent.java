package com.medplus.marketing_automation_backend.event;

/**
 * Published when a requestor approves a task in REQUESTOR_QC_REVIEW state.
 * The worker who completed the task is notified.
 *
 * @param taskId        the approved task
 * @param requestorName full name of the requestor who approved
 * @param workerId      user ID of the task's assigned worker to notify
 */
public record RequestorQcApprovedEvent(String taskId, String requestorName, int workerId) {}
