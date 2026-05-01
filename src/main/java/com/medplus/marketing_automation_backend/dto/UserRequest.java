package com.medplus.marketing_automation_backend.dto;

import com.medplus.marketing_automation_backend.enums.RecordStatus;
import com.medplus.marketing_automation_backend.enums.SkillLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    @Email
    private String email;

    private String       departmentId;
    /** All roles to assign to the user (replaces previous single roleId). */
    private List<String> roleIds;
    private String       designationId;
    private SkillLevel   skillLevel;
    private RecordStatus status;
}
