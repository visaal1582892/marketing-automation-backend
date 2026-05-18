package com.medplus.marketing_automation_backend.event;

/**
 * Published when the designer submits their source task for QC and the linked
 * content task is auto-closed (marked complete because the writer had started it).
 * The content writer is notified that their task has been marked as complete.
 *
 * @param contentTaskId  the auto-generated WORK-TASK-xxx identifier
 * @param assignedUserId the content writer to notify
 */
public record ContentTaskAutoClosedEvent(String contentTaskId, int assignedUserId) {}
