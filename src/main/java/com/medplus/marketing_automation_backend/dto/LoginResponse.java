package com.medplus.marketing_automation_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String      token;
    private String      tokenType;
    private long        expiresInMs;
    private UserSummary user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        private Long         id;
        private String       fullName;
        private String       email;
        /** Primary role name — used for profile display. */
        private String       role;
        /** All role names the user holds (may be multiple). */
        private List<String> roles;
        /** All role IDs the user holds (parallel to roles). */
        private List<String> roleIds;
        private String       departmentId;
        private String       department;
        private String       designationId;
        private String       designation;
    }
}
