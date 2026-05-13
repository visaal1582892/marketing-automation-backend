package com.medplus.marketing_automation_backend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Maps a worker role to a manager role for QC task routing. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QcRouting {
    private Integer id;
    private String  workerRoleId;
    private String  managerRoleId;
}
