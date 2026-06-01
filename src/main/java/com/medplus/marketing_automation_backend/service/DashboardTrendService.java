package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.dto.DashboardTrendDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardTrendService {

    private final JdbcTemplate jdbc;

    /** Returns 7-day daily sparklines + today-vs-yesterday trend % for ops KPIs. */
    public DashboardTrendDTO getOpsTrend() {
        LocalDate today    = LocalDate.now();
        LocalDate weekAgo  = today.minusDays(6);

        List<Integer> qcReview   = dailyCounts("submitted_at",           weekAgo, today, null);
        List<Integer> rework     = dailyCounts("created_at",             weekAgo, today, "REWORK");
        List<Integer> inProgress = dailyCounts("accepted_at",            weekAgo, today, "IN_PROGRESS");
        List<Integer> completed  = dailyCounts("requestor_approved_at",  weekAgo, today, null);
        List<Integer> assigned   = dailyCounts("assigned_at",            weekAgo, today, null);
        List<Integer> held       = dailyCounts("created_at",             weekAgo, today, "HELD");
        List<Integer> cancelled  = dailyCounts("created_at",             weekAgo, today, "CANCELLED");

        return DashboardTrendDTO.builder()
                .qcReview(qcReview)
                .rework(rework)
                .inProgress(inProgress)
                .completed(completed)
                .assigned(assigned)
                .held(held)
                .cancelled(cancelled)
                .qcReviewTrend(trendPct(qcReview))
                .reworkTrend(trendPct(rework))
                .inProgressTrend(trendPct(inProgress))
                .completedTrend(trendPct(completed))
                .assignedTrend(trendPct(assigned))
                .heldTrend(trendPct(held))
                .cancelledTrend(trendPct(cancelled))
                .build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a 7-element list (oldest→today) where each entry is the count of
     * work_tasks rows whose {@code dateCol} falls on that day.
     * Optional {@code status} filters by task status (null = any status).
     */
    private List<Integer> dailyCounts(String dateCol, LocalDate from, LocalDate to, String status) {
        String sql = status != null
            ? "SELECT DATE(" + dateCol + ") AS day, COUNT(*) AS cnt " +
              "FROM work_tasks " +
              "WHERE " + dateCol + " >= ? AND " + dateCol + " < ? + INTERVAL 1 DAY " +
              "  AND status = ? " +
              "GROUP BY DATE(" + dateCol + ") ORDER BY day"
            : "SELECT DATE(" + dateCol + ") AS day, COUNT(*) AS cnt " +
              "FROM work_tasks " +
              "WHERE " + dateCol + " >= ? AND " + dateCol + " < ? + INTERVAL 1 DAY " +
              "GROUP BY DATE(" + dateCol + ") ORDER BY day";

        // Build date → count map
        Map<LocalDate, Integer> byDate = new HashMap<>();
        try {
            List<Map<String, Object>> rows = status != null
                ? jdbc.queryForList(sql, from, to, status)
                : jdbc.queryForList(sql, from, to);
            for (Map<String, Object> row : rows) {
                Object dayObj = row.get("day");
                if (dayObj == null) continue;
                LocalDate day = (dayObj instanceof LocalDate ld) ? ld
                              : ((java.sql.Date) dayObj).toLocalDate();
                byDate.put(day, ((Number) row.get("cnt")).intValue());
            }
        } catch (Exception ignored) { /* return zeroes on any error */ }

        // Fill 7 consecutive days, zeroing missing ones
        List<Integer> result = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            result.add(byDate.getOrDefault(from.plusDays(i), 0));
        }
        return result;
    }

    /** % change: (today − yesterday) / yesterday * 100, rounded. null if yesterday = 0. */
    private static Integer trendPct(List<Integer> series) {
        if (series == null || series.size() < 2) return null;
        int today     = series.get(series.size() - 1);
        int yesterday = series.get(series.size() - 2);
        if (yesterday == 0) return today > 0 ? 100 : null;
        return (int) Math.round((today - yesterday) * 100.0 / yesterday);
    }
}
