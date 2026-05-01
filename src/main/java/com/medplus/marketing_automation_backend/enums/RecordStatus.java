package com.medplus.marketing_automation_backend.enums;

/**
 * Unified active/inactive status used by every master lookup table and users.
 * Replaces the old {@code is_active ENUM('YES','NO')} + {@code db_status ENUM('ACTIVE','INACTIVE')} pair.
 */
public enum RecordStatus {
    ACTIVE,
    INACTIVE
}
