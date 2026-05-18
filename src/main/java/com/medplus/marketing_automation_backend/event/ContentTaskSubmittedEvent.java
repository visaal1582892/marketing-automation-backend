package com.medplus.marketing_automation_backend.event;

/**
 * Published when a content writer completes (submits) their auto-generated task.
 * The graphic designer who requested the content is notified.
 *
 * @param contentTaskId  the auto-generated WORK-TASK-xxx identifier
 * @param writerName     display name of the content writer
 * @param designerUserId user ID of the graphic designer to notify
 */
public record ContentTaskSubmittedEvent(String contentTaskId, String writerName, int designerUserId) {}
