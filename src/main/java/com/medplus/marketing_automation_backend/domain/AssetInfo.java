package com.medplus.marketing_automation_backend.domain;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetInfo {
    private Integer       assetId;
    private String        taskId;
    private Integer       userId;
    private String        userName;
    private String        url;
    private LocalDateTime createdAt;
}
