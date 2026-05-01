package com.medplus.marketing_automation_backend.domain;

import com.medplus.marketing_automation_backend.enums.RecordStatus;
import com.medplus.marketing_automation_backend.enums.SkillLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long          userId;
    private String        fullName;
    private String        email;
    private String        passwordHash;
    private String        departmentId;
    private String        departmentName;
    /** All roles the user holds — populated via user_roles junction table. */
    @Builder.Default
    private List<String>  roleIds   = Collections.emptyList();
    @Builder.Default
    private List<String>  roleNames = Collections.emptyList();
    private String        designationId;
    private String        designationName;
    private SkillLevel    skillLevel;
    private Integer       currentActiveTasks;
    private RecordStatus  status;
    private LocalDateTime createdAt;

    /** The first role (ordered by role_id) — used for display in the profile header and JWT. */
    public String getPrimaryRoleId() {
        return (roleIds != null && !roleIds.isEmpty()) ? roleIds.get(0) : null;
    }

    /** The first role name (ordered by role_id) — used for display in the profile header and JWT. */
    public String getPrimaryRoleName() {
        return (roleNames != null && !roleNames.isEmpty()) ? roleNames.get(0) : null;
    }

    /** True if this user holds the given role name (case-insensitive). */
    public boolean hasRole(String roleName) {
        if (roleNames == null || roleName == null) return false;
        return roleNames.stream().anyMatch(r -> r.equalsIgnoreCase(roleName));
    }
}
