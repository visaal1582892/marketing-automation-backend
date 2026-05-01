package com.medplus.marketing_automation_backend.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long          userId;
    private String        fullName;
    private String        email;
    private String        departmentId;
    private String        departmentName;
    /** Primary role name — for display (profile header, user table). */
    private String        role;
    /** All role IDs the user holds. */
    private List<String>  roleIds;
    /** All role names the user holds (parallel to roleIds). */
    private List<String>  roleNames;
    private String        designationId;
    private String        designationName;
    private String        skillLevel;
    private Integer       currentActiveTasks;
    private String        status;
    private LocalDateTime createdAt;
}
