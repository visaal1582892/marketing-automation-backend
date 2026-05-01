package com.medplus.marketing_automation_backend.domain;

import com.medplus.marketing_automation_backend.enums.RecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wire format for any master record.
 *
 *  - id     : human-friendly code such as ROLE-1 (read-only, server assigned).
 *  - name   : the display label.
 *  - status : ACTIVE (visible to users) or INACTIVE (hidden from dropdowns).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterItem {

    private String id;

    @NotBlank
    @Size(max = 255)
    private String name;

    private RecordStatus status;
}
