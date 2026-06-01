package com.medplus.marketing_automation_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 7-day daily sparkline data for each ops KPI.
 * Each list has exactly 7 entries ordered oldest → today.
 * trendPct fields are % change from day 6 (yesterday) to day 7 (today).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTrendDTO {

    private List<Integer> qcReview;
    private List<Integer> rework;
    private List<Integer> inProgress;
    private List<Integer> completed;
    private List<Integer> assigned;
    private List<Integer> held;
    private List<Integer> cancelled;

    // % change today vs yesterday — positive = up, negative = down, null = no yesterday data
    private Integer qcReviewTrend;
    private Integer reworkTrend;
    private Integer inProgressTrend;
    private Integer completedTrend;
    private Integer assignedTrend;
    private Integer heldTrend;
    private Integer cancelledTrend;
}
