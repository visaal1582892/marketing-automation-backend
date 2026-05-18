package com.medplus.marketing_automation_backend.event;

/**
 * Published when a new chat message is posted in a task collaboration.
 * NotificationService resolves all other collaborators and notifies them.
 *
 * @param taskId      the task the message belongs to
 * @param senderId    user ID of the message author
 * @param senderName  full name of the message author
 */
public record NewTaskMessageEvent(String taskId, int senderId, String senderName) {}
