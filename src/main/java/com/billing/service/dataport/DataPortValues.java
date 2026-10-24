package com.billing.service.dataport;

import com.billing.dto.dataport.DataPortRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** DataPort ke common parse/normalize helpers (har module yahi use kare). */
public final class DataPortValues {

    private DataPortValues() {
    }

    public static final Set<String> TRUE_VALUES = Set.of("yes", "y", "true", "1", "active");
    public static final Set<String> FALSE_VALUES = Set.of("no", "n", "false", "0", "inactive");

    // STRICT: "31-02-2026" ko chupchaap 28 Feb na bana de — galat date error de.
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            strict("d-M-uuuu"),
            strict("d/M/uuuu"),
            strict("d.M.uuuu"),
            strict("uuuu-M-d"),
            strict("d-MMM-uuuu"),
            strict("d MMM uuuu"));

    private static DateTimeFormatter strict(String pattern) {
        return new java.time.format.DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.ENGLISH)
                .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    }

    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    public static String key(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").replace("₹", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static BigDecimal money(String value) {
        BigDecimal parsed = decimal(value);
        return parsed == null ? null : parsed.setScale(2, RoundingMode.HALF_UP);
    }

    public static Integer integer(String value) {
        BigDecimal parsed = decimal(value);
        if (parsed == null) {
            return null;
        }
        try {
            return parsed.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    public static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // agla format try karo
            }
        }
        return null;
    }

    /** null = blank, warna true/false; galat value par IllegalArgumentException. */
    public static Boolean bool(String value) {
        String normalized = key(value);
        if (normalized.isEmpty()) {
            return null;
        }
        if (TRUE_VALUES.contains(normalized)) {
            return true;
        }
        if (FALSE_VALUES.contains(normalized)) {
            return false;
        }
        throw new IllegalArgumentException(value);
    }

    public static boolean boolOrDefault(String value, boolean fallback) {
        Boolean parsed = bool(value);
        return parsed == null ? fallback : parsed;
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Upload ke andar duplicate: same key wali har row par error. */
    public static void markDuplicates(List<DataPortRow> rows, String field, Function<DataPortRow, String> keyFn, String message) {
        Map<String, Integer> counts = new HashMap<>();
        for (DataPortRow row : rows) {
            String rowKey = keyFn.apply(row);
            if (rowKey != null && !rowKey.isBlank()) {
                counts.merge(rowKey, 1, Integer::sum);
            }
        }
        for (DataPortRow row : rows) {
            String rowKey = keyFn.apply(row);
            if (rowKey != null && !rowKey.isBlank() && counts.getOrDefault(rowKey, 0) > 1) {
                row.error(field, message);
            }
        }
    }

    public static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = error.getMessage() != null && !error.getMessage().isBlank() ? error.getMessage() : current.getMessage();
        return message == null || message.isBlank() ? "Could not save this row" : message;
    }
}
