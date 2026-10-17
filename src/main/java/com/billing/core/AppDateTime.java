package com.billing.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Single static place for "India time" decisions.
 *
 * Storage stays UTC (JPA auditing, OTP/refresh expiries, MySQL NOW()): those
 * compare wall-clock against wall-clock, so no zone math is needed there.
 * Everything the USER SEES or that defines a CALENDAR DAY (PDF print time,
 * email/SMS Current_Date, Today/Week/Month filters, audit-range bounds,
 * createdAt-to-date comparisons) must go through here — Asia/Kolkata.
 *
 * Rule: never call LocalDate.now()/LocalDateTime.now() for display or
 * day-boundaries. Use istNow()/istToday(). Day for OTP-style durations
 * doesn't matter (same clock both sides) — leave those alone.
 */
public final class AppDateTime {

    /** Display/business zone for the whole product. Change once, here. */
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * The ONE display format for user-visible dates/times everywhere
     * (emails, SMS, PDFs, AI answers): "17-10-2026" / "17-10-2026 03:45 PM".
     * Frontend mirrors this in src/lib/format.ts (formatDate/formatDateTime).
     */
    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);
    public static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a", Locale.ENGLISH);

    private AppDateTime() {
    }

    /** Now in IST — server-rendered timestamps (PDF print time, API stamp). */
    public static LocalDateTime istNow() {
        return LocalDateTime.now(IST);
    }

    /** Today in IST — calendar-day boundaries (Today/Week/Month filters). */
    public static LocalDate istToday() {
        return LocalDate.now(IST);
    }

    /** dd-MM-yyyy display text; null-safe ("--" when absent). */
    public static String formatDate(LocalDate date) {
        return date == null ? "--" : DISPLAY_DATE.format(date);
    }

    /** dd-MM-yyyy hh:mm a display text; null-safe ("--" when absent). */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "--" : DISPLAY_DATE_TIME.format(dateTime);
    }

    /**
     * Stored UTC wall-time -> IST calendar date, for comparing stored
     * datetimes against user-facing (IST) day ranges.
     */
    public static LocalDate toIstDate(LocalDateTime storedUtc) {
        if (storedUtc == null) {
            return null;
        }
        return storedUtc.atZone(ZoneId.of("UTC")).withZoneSameInstant(IST).toLocalDate();
    }

    /**
     * IST calendar day -> UTC wall-time bounds, for querying UTC-stored
     * datetimes with a user-picked (IST) date range.
     */
    public static LocalDateTime[] istDayBounds(LocalDate istDay) {
        if (istDay == null) {
            return new LocalDateTime[]{null, null};
        }
        LocalDateTime startIst = istDay.atStartOfDay();
        LocalDateTime endIst = istDay.atTime(LocalTime.MAX);
        return new LocalDateTime[]{
                startIst.atZone(IST).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime(),
                endIst.atZone(IST).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime()
        };
    }
}
