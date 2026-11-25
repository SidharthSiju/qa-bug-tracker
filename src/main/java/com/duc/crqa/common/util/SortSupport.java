package com.duc.crqa.common.util;

import java.util.Set;
import org.springframework.data.domain.Sort;

public final class SortSupport {

    private SortSupport() {
    }

    public static Sort resolve(String field, String direction, Set<String> allowed, Sort fallback) {
        if (field == null || field.isBlank() || allowed == null || !allowed.contains(field)) {
            return fallback;
        }
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field);
    }
}
