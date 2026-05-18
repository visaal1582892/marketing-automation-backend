package com.medplus.marketing_automation_backend.event;

/**
 * Published when a worker submits their task for manager QC review.
 * NotificationService resolves which managers to notify.
 *
 * @param taskId     the submitted task
 * @param workerId   user ID of the submitting worker
 * @param workerName full name of the submitting worker
 */
public record TaskSubmittedForQcEvent(String taskId, int workerId, String workerName) {}
