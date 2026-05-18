package com.medplus.marketing_automation_backend.event;

import java.util.List;

/**
 * Published when a requestor deletes an entire campaign.
 * All workers who had assigned tasks on that campaign are notified.
 *
 * @param campaignId      the campaign that was deleted
 * @param campaignName    display name of the deleted campaign
 * @param assignedUserIds user IDs of all workers who had tasks on this campaign
 */
public record CampaignDeletedEvent(int campaignId, String campaignName, List<Integer> assignedUserIds) {}
