package com.medplus.marketing_automation_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class CampaignBookmarkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CampaignBookmarkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Toggles the bookmark for (userId, campaignId).
     * Inserts if not present; deletes if already present.
     *
     * @return {@code true} if the campaign is now bookmarked, {@code false} if removed.
     */
    public boolean toggle(int userId, int campaignId) {
        boolean exists = isBookmarked(userId, campaignId);
        if (exists) {
            jdbc.update(
                    "DELETE FROM campaign_bookmarks WHERE user_id = :uid AND campaign_id = :cid",
                    new MapSqlParameterSource("uid", userId).addValue("cid", campaignId));
            return false;
        } else {
            jdbc.update(
                    "INSERT IGNORE INTO campaign_bookmarks (user_id, campaign_id) VALUES (:uid, :cid)",
                    new MapSqlParameterSource("uid", userId).addValue("cid", campaignId));
            return true;
        }
    }

    public boolean isBookmarked(int userId, int campaignId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campaign_bookmarks WHERE user_id = :uid AND campaign_id = :cid",
                new MapSqlParameterSource("uid", userId).addValue("cid", campaignId),
                Integer.class);
        return count != null && count > 0;
    }

    /** Returns the set of campaign IDs bookmarked by a user — used for bulk enrichment. */
    public Set<Integer> findBookmarkedCampaignIds(int userId) {
        List<Integer> ids = jdbc.queryForList(
                "SELECT campaign_id FROM campaign_bookmarks WHERE user_id = :uid",
                new MapSqlParameterSource("uid", userId),
                Integer.class);
        return new HashSet<>(ids);
    }
}
