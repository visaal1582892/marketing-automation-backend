package com.medplus.marketing_automation_backend.event;

/**
 * Published when a requestor/manager marks a worker comment as answered.
 * The worker (task assignee) is notified that their question was addressed.
 *
 * @param taskId         the WORK-TASK-xxx identifier
 * @param responderName  display name of the person who marked the comment answered
 * @param assignedUserId the worker to notify
 */
public record CommentRespondedEvent(String taskId, String responderName, int assignedUserId) {}
