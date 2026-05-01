package com.medplus.marketing_automation_backend.dto;

import lombok.Data;

import java.util.List;

/**
 * Payload sent by the creator when marking an in-progress task as complete
 * and submitting it for QC review (Module 3 / Module 4 boundary).
 *
 * assetUrls (preferred): list of full URLs returned by POST /api/upload/asset.
 *   Stored as a JSON array string in work_tasks.asset_url.
 * assetUrl  (legacy):    single URL string kept for backward compatibility.
 *   Used only when assetUrls is absent or empty.
 */
@Data
public class TaskSubmissionRequest {
    private String       submissionNotes;
    private List<String> assetUrls;
    /** @deprecated prefer assetUrls; retained for backward compatibility */
    private String       assetUrl;
}
