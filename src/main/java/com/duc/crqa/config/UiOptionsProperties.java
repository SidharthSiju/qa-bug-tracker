package com.duc.crqa.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Values offered by the UI Settings dropdowns, configured in application.yml as comma-separated values, e.g.
 *
 * <pre>
 * app:
 *   ui:
 *     page-sizes: 10,25,50,100
 *     font-sizes: 10,12,14,16
 * </pre>
 *
 * A YAML list is accepted too. Blank or non-numeric entries are ignored, and the built-in defaults are used when the
 * configured list ends up empty.
 */
@Component
@ConfigurationProperties(prefix = "app.ui")
@Getter
@Setter
public class UiOptionsProperties {

    private static final List<Integer> DEFAULT_PAGE_SIZES = List.of(10, 25, 50, 100);
    private static final List<Integer> DEFAULT_FONT_SIZES = List.of(10, 11, 12);

    private List<String> pageSizes = List.of("10", "25", "50", "100");

    private List<String> fontSizes = List.of("10", "11", "12");

    public List<Integer> pageSizeOptions() {
        return parse(pageSizes, DEFAULT_PAGE_SIZES);
    }

    public List<Integer> fontSizeOptions() {
        return parse(fontSizes, DEFAULT_FONT_SIZES);
    }

    private static List<Integer> parse(List<String> values, List<Integer> fallback) {
        LinkedHashSet<Integer> parsed = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                for (String token : value.split(",")) {
                    String trimmed = token.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    try {
                        int number = Integer.parseInt(trimmed);
                        if (number > 0) {
                            parsed.add(number);
                        }
                    } catch (NumberFormatException ignored) {
                        // ignore non-numeric entries
                    }
                }
            }
        }
        return parsed.isEmpty() ? fallback : new ArrayList<>(parsed);
    }
}
