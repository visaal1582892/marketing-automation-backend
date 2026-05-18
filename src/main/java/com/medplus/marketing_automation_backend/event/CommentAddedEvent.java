package com.medplus.marketing_automation_backend.event;

/**
 * Published when a worker adds a comment (and self-holds their task).
 * The campaign requestor is notified.
 *
 * @param taskId      the WORK-TASK-xxx identifier
 * @param workerName  display name of the worker who added the comment
 * @param requestorId user ID of the campaign requestor to notify
 */
public record CommentAddedEvent(String taskId, String workerName, int requestorId) {}
