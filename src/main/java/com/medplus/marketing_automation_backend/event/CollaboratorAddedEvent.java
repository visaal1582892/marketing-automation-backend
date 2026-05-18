package com.medplus.marketing_automation_backend.event;

import java.util.List;

/**
 * Published when one or more users are invited to collaborate on a task.
 *
 * @param taskId       the task they were added to
 * @param userIds      list of newly-added collaborator user IDs
 * @param inviterName  full name of the person who sent the invite
 */
public record CollaboratorAddedEvent(String taskId, List<Integer> userIds, String inviterName) {}
