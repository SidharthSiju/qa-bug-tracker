package com.duc.crqa.common.util;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Parses a date written in any of the common formats a hand-made CSV tends to carry — ISO ({@code 2026-10-01}), a
 * year-first numeric date ({@code 2026/10/01}, {@code 20261001}), a month-name date ({@code 1-Oct-2026},
 * {@code Oct 1, 2026}) or a numeric day/month/year date ({@code 01/10/2026}) — and returns it as a {@link LocalDate}
 * ready to be stored. A trailing time is accepted and dropped.
 *
 * <p>When a numeric date is ambiguous (both parts are 12 or less, so {@code 03/04/2026} could be 3 April or 4 March)
 * it is read <strong>day first</strong>, matching the day-first convention used elsewhere in the application.
 */
public final class DateParser {

    private static final List<DateTimeFormatter> FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yy", Locale.ENGLISH));

    private DateParser() {
    }

    /** The parsed date, or {@code null} when the value is blank or is not a recognised date. */
    public static LocalDate parse(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        LocalDate date = parseDate(text);
        if (date != null) {
            return date;
        }
        // The value may flip a date and a time ("2026-10-01 00:00:00", "01/10/2026 09:15", "…T00:00:00Z"): retry on
        // the date half alone.
        String dateHalf = dateHalf(text);
        return dateHalf.equals(text) ? null : parseDate(dateHalf);
    }

    private static LocalDate parseDate(String text) {
        for (DateTimeFormatter format : FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return parseDayMonthYear(text);
    }

    /** The part of a value before a time ({@code T} or a space), or the whole value when it carries no time. */
    private static String dateHalf(String text) {
        int cut = text.indexOf('T');
        int space = text.indexOf(' ');
        if (space >= 0 && (cut < 0 || space < cut)) {
            cut = space;
        }
        return cut < 0 ? text : text.substring(0, cut).trim();
    }

    /**
     * A numeric day/month/year date whose year is the last component ({@code d/M/yyyy}, {@code M/d/yyyy} and the
     * dash/dot equivalents). A value greater than 12 fixes which part is the day; otherwise the day is read first.
     */
    private static LocalDate parseDayMonthYear(String text) {
        String[] parts = text.split("[/.\\-]");
        if (parts.length != 3) {
            return null;
        }
        Integer first = parseInt(parts[0]);
        Integer second = parseInt(parts[1]);
        Integer year = parseYear(parts[2]);
        if (first == null || second == null || year == null) {
            return null;
        }
        int day;
        int month;
        if (first > 12 && second <= 12) {
            day = first;
            month = second;
        } else if (second > 12 && first <= 12) {
            day = second;
            month = first;
        } else if (first <= 12 && second <= 12) {
            day = first;
            month = second;
        } else {
            return null;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A four-digit year, or a two-digit year read as 2000+. */
    private static Integer parseYear(String value) {
        String trimmed = value.trim();
        Integer year = parseInt(trimmed);
        if (year == null) {
            return null;
        }
        if (trimmed.length() == 4) {
            return year;
        }
        if (trimmed.length() == 2) {
            return 2000 + year;
        }
        return null;
    }
}
