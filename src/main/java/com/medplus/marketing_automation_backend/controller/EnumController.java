package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.enums.Priority;
import com.medplus.marketing_automation_backend.enums.TaskCategory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Exposes the remaining Java-enum values that are NOT yet master-table-driven.
 * All form-field dropdowns (business objective, language, tone, supporting proof,
 * budget tier, kpi type, expected output, vendor type, etc.) are now served
 * from the master-data API (/api/master/...). This controller retains only:
 *   - priorities  (system concept — not admin-editable)
 *   - taskCategories (internal concept)
 */
@RestController
@RequestMapping("/api/enums")
@PreAuthorize("isAuthenticated()")
public class EnumController {

    @GetMapping("/campaign-form")
    public Map<String, List<Map<String, String>>> campaignFormOptions() {
        return Map.of(
            "priorities",     options(Priority.values(),     Priority::getLabel),
            "taskCategories", options(TaskCategory.values(), TaskCategory::getLabel)
        );
    }

    private static <E extends Enum<E>> List<Map<String, String>> options(
            E[] values, Function<E, String> labelFn) {
        return Arrays.stream(values)
                .map(e -> Map.of("value", e.name(), "label", labelFn.apply(e)))
                .toList();
    }
}
