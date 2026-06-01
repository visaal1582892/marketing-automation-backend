package com.medplus.marketing_automation_backend.dto;

import java.util.List;
import java.util.Map;

/**
 * Paginated response for the "My Tasks" employee view.
 *
 * @param content       Tasks on the current page (enriched, with canStart flag set).
 * @param totalElements Total tasks matching the active tab + search filter.
 * @param totalPages    Number of pages for the active filter.
 * @param page          0-based current page index.
 * @param size          Page size.
 * @param inFlightFull  True when the user already has 3 tasks IN_PROGRESS — used by
 *                      the UI to show "3 tasks active" vs "start a top task first" lock text.
 * @param counts        Tab badge counts across ALL user tasks (independent of search/tab filter).
 *                      Keys: open, held, qc, done, cancelled, all.
 */
public record MyTasksListResponse(
        List<WorkTaskResponse> content,
        long                   totalElements,
        int                    totalPages,
        int                    page,
        int                    size,
        boolean                inFlightFull,
        Map<String, Long>      counts
) {}
