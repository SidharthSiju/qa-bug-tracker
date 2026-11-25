package com.duc.crqa.common.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BulkUploadResult {

    private int totalRows;
    private int successCount;
    private int errorCount;
    private List<RowError> errors = new ArrayList<>();

    public record RowError(int rowNumber, String message) {
    }

    public void addSuccess() {
        successCount++;
        totalRows++;
    }

    public void addError(int rowNumber, String message) {
        errors.add(new RowError(rowNumber, message));
        errorCount++;
        totalRows++;
    }

    /**
     * Prefixes a row error with a reference that names the row's business key(s) — for example its epic/CR number — so
     * the offending line can be found in the uploaded file without counting rows. The non-blank parts are joined with
     * ", "; when none has a value the message is returned unchanged.
     */
    public static String withReference(String message, String... referenceParts) {
        StringBuilder reference = new StringBuilder();
        for (String part : referenceParts) {
            if (part != null && !part.isBlank()) {
                if (reference.length() > 0) {
                    reference.append(", ");
                }
                reference.append(part.trim());
            }
        }
        return reference.length() == 0 ? message : reference.append(": ").append(message).toString();
    }

    /** A labelled reference part such as {@code "Epic/CR OKQABUG-1"}; returns {@code null} when the value is blank. */
    public static String part(String label, String value) {
        return value == null || value.isBlank() ? null : label + " " + value.trim();
    }
}
