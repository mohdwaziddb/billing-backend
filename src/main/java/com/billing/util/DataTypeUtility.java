package com.billing.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Central utility for safe data-type conversions.
 * Copied main methods from TSM DataTypeUtility with proper null / edge checks.
 * Source: D/tsm-svn/src/main/java/com/application/sis/utility/DataTypeUtility.java
 * Keep this class stateless and null-safe - every method must handle null, "null", "undefined", "all", "" gracefully.
 */
public final class DataTypeUtility {

    private DataTypeUtility() {
    }

    // ========== String ==========

    public static String stringValue(Object value) {
        if (value == null || value.toString().equalsIgnoreCase("undefined") || value.toString().equalsIgnoreCase("null")) {
            return "";
        }
        return value.toString().trim();
    }

    public static String stringNullValue(Object value) {
        if (value == null || value.toString().length() == 0 || value.toString().equalsIgnoreCase("null")) {
            return null;
        }
        return value.toString().trim();
    }

    public static String stringValueWithNA(Object value) {
        String s = stringValue(value);
        return s.length() == 0 ? "NA" : s;
    }

    // ========== Long ==========

    public static Long longValue(Object value) {
        try {
            if (value instanceof String) {
                String svalue = stringValue(value);
                if (svalue.equalsIgnoreCase("all") || svalue.equalsIgnoreCase("null") || svalue.length() == 0) {
                    return null;
                }
                if (svalue.contains("null")) {
                    return null;
                }
                try {
                    return Long.valueOf(String.valueOf(value).trim());
                } catch (Exception e) {
                    Float f = floatObjectValue(value);
                    if (f == null) return null;
                    return (long) (float) f;
                }
            }
            if (value instanceof Double) {
                return (long) (float) (double) (Double) value;
            }
            if (value instanceof Float) {
                return (long) (float) (Float) value;
            }
            if (value instanceof BigInteger) {
                return ((BigInteger) value).longValue();
            }
            if (value instanceof BigDecimal) {
                return ((BigDecimal) value).longValue();
            }
            if (value instanceof Integer) {
                return (long) (int) (Integer) value;
            }
            if (value instanceof Byte) {
                return (long) (byte) value;
            }
            if (value instanceof Short) {
                return (long) (short) value;
            }
            return (value == null ? null : (Long) value);
        } catch (Exception e) {
            return null;
        }
    }

    public static Long longZeroValue(Object value) {
        try {
            if (value instanceof String) {
                String svalue = stringValue(value);
                if (svalue.equalsIgnoreCase("null") || svalue.length() == 0) {
                    return 0L;
                }
                return Long.parseLong(svalue);
            }
            if (value instanceof Double) {
                return (long) (float) (double) (Double) value;
            }
            if (value instanceof BigInteger) {
                return ((BigInteger) value).longValue();
            }
            if (value instanceof BigDecimal) {
                return ((BigDecimal) value).longValue();
            }
            if (value instanceof Float) {
                return (long) (float) (Float) value;
            }
            if (value instanceof Integer) {
                return (long) (int) (Integer) value;
            }
            if (value instanceof Short) {
                return (long) (short) value;
            }
            if (value instanceof Byte) {
                return (long) (byte) value;
            }
            return (value == null ? 0L : (Long) value);
        } catch (Exception e) {
            return 0L;
        }
    }

    public static Long getForeignKeyValue(Object value) {
        if (value == null) {
            return null;
        }
        Long val = longValue(value);
        if (val == null || val <= 0L) {
            return null;
        }
        return val;
    }

    public static Long getForeignKeyValue(Long value) {
        if (value == null || value <= 0L) {
            return null;
        }
        return value;
    }

    public static Long getForeignKeyZeroValue(Long value) {
        if (value == null || value <= 0L) {
            return 0L;
        }
        return value;
    }

    public static boolean requireNonNullLong(Long value) {
        return value != null && value > 0;
    }

    public static boolean requireNonNullCollection(Collection<?> c) {
        return c != null && !c.isEmpty();
    }

    // ========== Integer ==========

    public static int integerValue(Object value) {
        if (value instanceof String) {
            String svalue = stringValue(value);
            if (svalue.length() == 0 || svalue.equalsIgnoreCase("null")) {
                return 0;
            }
            try {
                return Integer.parseInt(svalue);
            } catch (NumberFormatException e) {
                // fallback via double
                try {
                    return (int) Double.parseDouble(svalue);
                } catch (Exception ex) {
                    return 0;
                }
            }
        }
        if (value instanceof Double) {
            return (int) (double) (Double) value;
        }
        if (value instanceof Float) {
            return (int) (float) (Float) value;
        }
        if (value instanceof Long) {
            return (int) (long) (Long) value;
        }
        if (value instanceof BigInteger) {
            return ((BigInteger) value).intValue();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return (value == null ? 0 : (Integer) value);
    }

    public static Integer integerNullValue(Object value) {
        if (value instanceof String) {
            String svalue = stringValue(value);
            if (svalue.length() == 0) {
                return null;
            }
            try {
                return Integer.parseInt(svalue);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (value instanceof Float) {
            return (int) (float) (Float) value;
        }
        if (value instanceof Double) {
            return (int) (double) (Double) value;
        }
        if (value instanceof Long) {
            return (int) (long) (Long) value;
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).intValue();
        }
        if (value instanceof BigInteger) {
            return ((BigInteger) value).intValue();
        }
        return (value == null ? null : (Integer) value);
    }

    // ========== Boolean ==========

    public static boolean booleanValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Integer) {
            return ((Integer) value) == 1;
        } else if (value instanceof Long) {
            return ((Long) value) == 1L;
        } else if (value instanceof Short) {
            return ((Short) value) == 1;
        } else if (value instanceof Byte) {
            return ((Byte) value) == 1;
        } else if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.equalsIgnoreCase("on") || str.equalsIgnoreCase("true") || str.equalsIgnoreCase("yes") || str.equalsIgnoreCase("1")) {
                return true;
            }
        }
        return false;
    }

    // ========== Float / Double / BigDecimal ==========

    public static float floatValue(Object value) {
        if (value instanceof Float) {
            return ((Float) value).floatValue();
        } else if (value instanceof Integer) {
            return ((Integer) value).intValue();
        } else if (value instanceof Long) {
            return ((Long) value).longValue();
        } else if (value instanceof String) {
            String val = stringValue(value);
            if (val.length() > 0) {
                try {
                    return Float.parseFloat(val);
                } catch (NumberFormatException e) {
                    return 0f;
                }
            } else {
                return 0f;
            }
        }
        if (value instanceof Double) {
            return ((Double) value).floatValue();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).floatValue();
        }
        if (value instanceof BigInteger) {
            return ((BigInteger) value).floatValue();
        }
        return (value == null ? 0f : (Float) value);
    }

    public static Float floatObjectValue(Object value) {
        try {
            if (value instanceof Float) {
                return ((Float) value).floatValue();
            } else if (value instanceof Integer) {
                return (float) ((Integer) value).intValue();
            } else if (value instanceof Long) {
                return (float) ((Long) value).longValue();
            } else if (value instanceof String) {
                if (stringValue(value).length() > 0) {
                    return (float) Double.parseDouble(stringValue(value));
                }
                return null;
            } else if (value instanceof Double) {
                return (float) ((Double) value).doubleValue();
            } else if (value instanceof BigDecimal) {
                return ((BigDecimal) value).floatValue();
            } else if (value instanceof BigInteger) {
                return ((BigInteger) value).floatValue();
            }
            return (value == null ? null : (Float) value);
        } catch (Exception e) {
            return 0f;
        }
    }

    public static Float floatZeroValue(Object value) {
        if (value != null && stringValue(value).length() != 0) {
            try {
                return Float.valueOf(value.toString());
            } catch (NumberFormatException e) {
                return 0f;
            }
        }
        return 0f;
    }

    public static Double doubleObjectValue(Object value) {
        try {
            if (value instanceof Double) {
                return ((Double) value).doubleValue();
            } else if (value instanceof Float) {
                return (double) ((Float) value).floatValue();
            } else if (value instanceof Integer) {
                return (double) ((Integer) value).intValue();
            } else if (value instanceof Long) {
                return (double) ((Long) value).longValue();
            } else if (value instanceof String) {
                if (value.toString().length() > 0) {
                    return Double.parseDouble(value.toString());
                }
                return null;
            } else if (value instanceof BigDecimal) {
                return ((BigDecimal) value).doubleValue();
            } else if (value instanceof BigInteger) {
                return ((BigInteger) value).doubleValue();
            }
            return (value == null ? null : (Double) value);
        } catch (Exception e) {
            return null;
        }
    }

    public static Double doubleZeroValue(Object value) {
        if (value != null && !value.toString().equalsIgnoreCase("undefined") && value.toString().length() != 0) {
            try {
                if (value instanceof Float) {
                    return Double.valueOf(value.toString());
                }
                if (value instanceof Long) {
                    return (double) (long) (Long) value;
                }
                return Double.valueOf(value.toString());
            } catch (NumberFormatException e) {
                return 0d;
            }
        }
        return 0d;
    }

    public static BigDecimal bigDecimalObjectValue(Object value) {
        try {
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            } else if (value instanceof String) {
                if (stringValue(value).length() > 0) {
                    return new BigDecimal(stringValue(value));
                }
                return null;
            } else if (value instanceof Long) {
                return BigDecimal.valueOf((Long) value);
            } else if (value instanceof Integer) {
                return BigDecimal.valueOf((Integer) value);
            } else if (value instanceof Double) {
                return BigDecimal.valueOf((Double) value);
            } else if (value instanceof Float) {
                return BigDecimal.valueOf((Float) value);
            }
            return (value == null ? null : (BigDecimal) value);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ========== DB ==========

    public static Object getDBValue(Object value) {
        if (value == null || value.toString().length() == 0) {
            return null;
        }
        return value;
    }

    // ========== Collection helpers ==========

    public static Set<Long> getSetFromCommaValue(String str_data) {
        str_data = stringValue(str_data);
        if (str_data.length() == 0) {
            return new HashSet<>();
        }
        String[] str_array = str_data.split(",");
        Set<Long> set = new HashSet<>();
        for (String str : str_array) {
            str = stringValue(str.trim());
            if (str.length() > 0 && getForeignKeyValue(str) != null) {
                Long v = longValue(str);
                if (v != null) set.add(v);
            }
        }
        return set;
    }

    public static ArrayList<Long> getListFromCommaValue(String str_data) {
        str_data = stringValue(str_data);
        if (str_data.length() == 0) {
            return new ArrayList<>();
        }
        String[] str_array = str_data.split(",");
        ArrayList<Long> list = new ArrayList<>();
        for (String str : str_array) {
            str = stringValue(str.trim());
            if (str.length() > 0 && getForeignKeyValue(str) != null) {
                list.add(longValue(str));
            }
        }
        return list;
    }

    public static String getCommaValueFromSet(Set<Long> set) {
        if (set == null || set.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Long v : set) {
            if (sb.length() == 0) sb.append(v);
            else sb.append(", ").append(v);
        }
        return sb.toString();
    }

    public static String getCommaValueFilter(String itemstr, Long id) {
        if (id == null) return itemstr == null ? "" : itemstr;
        if (itemstr == null) itemstr = "";
        if (itemstr.length() == 0) return String.valueOf(id);
        return itemstr + "," + id;
    }

    public static String getCommaValueFilter(StringBuilder itemstr, Long id) {
        if (id == null) return itemstr == null ? "" : itemstr.toString();
        if (itemstr == null) return String.valueOf(id);
        if (itemstr.length() == 0) return String.valueOf(id);
        return itemstr.toString() + "," + id;
    }

    // ========== Date / Time ==========

    private static final String US_DATE_PATTERN = "yyyy-MM-dd";
    private static final String INDIAN_DATE_PATTERN = "dd-MM-yyyy";
    private static final String US_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String INDIAN_DATETIME_PATTERN = "dd-MM-yyyy HH:mm:ss";
    private static final String INDIAN_DATETIME_SLASH_PATTERN = "dd/MM/yyyy HH:mm:ss";

    public static Date getCurrentDate() {
        return new Date();
    }

    public static String getCurrentDateInUSFormat() {
        return new SimpleDateFormat(US_DATE_PATTERN).format(new Date());
    }

    public static String getCurrentDateInIndianFormat() {
        return new SimpleDateFormat(INDIAN_DATE_PATTERN).format(new Date());
    }

    public static String getCurrentDateTimeInUSFormat() {
        return new SimpleDateFormat(US_DATETIME_PATTERN).format(new Date());
    }

    public static String getUSDateFormat(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat(US_DATE_PATTERN).format(date);
    }

    public static String getIndianDateFormat(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat(INDIAN_DATE_PATTERN).format(date);
    }

    public static String getUSDateFormat(String indianDate) {
        if (stringValue(indianDate).length() == 0) return "";
        try {
            Date d = new SimpleDateFormat(INDIAN_DATE_PATTERN).parse(indianDate.trim());
            return new SimpleDateFormat(US_DATE_PATTERN).format(d);
        } catch (ParseException e) {
            try {
                // try slash format
                Date d = new SimpleDateFormat("dd/MM/yyyy").parse(indianDate.trim());
                return new SimpleDateFormat(US_DATE_PATTERN).format(d);
            } catch (ParseException ex) {
                return "";
            }
        }
    }

    public static String getIndianDateFormat(String usDate) {
        if (stringValue(usDate).length() == 0) return "";
        try {
            String clean = usDate.contains("T") ? usDate.replace("T", " ").split(" ")[0] : usDate.split(" ")[0];
            Date d = new SimpleDateFormat(US_DATE_PATTERN).parse(clean.trim());
            return new SimpleDateFormat(INDIAN_DATE_PATTERN).format(d);
        } catch (ParseException e) {
            return "";
        }
    }

    public static Date getDateObjectFromUSFormat(String usDate) {
        if (stringValue(usDate).length() == 0) return null;
        try {
            String clean = usDate.split(" ")[0].trim();
            if (clean.contains("/")) {
                return new SimpleDateFormat("yyyy/MM/dd").parse(clean);
            }
            return new SimpleDateFormat(US_DATE_PATTERN).parse(clean);
        } catch (ParseException e) {
            return null;
        }
    }

    public static Date getDateObjectFromIndianFormat(String indianDate) {
        if (stringValue(indianDate).length() == 0) return null;
        try {
            return new SimpleDateFormat(INDIAN_DATE_PATTERN).parse(indianDate.trim());
        } catch (ParseException e) {
            try {
                return new SimpleDateFormat("dd/MM/yyyy").parse(indianDate.trim());
            } catch (ParseException ex) {
                return null;
            }
        }
    }

    public static Date getDateObject(String dateStr, String pattern) {
        if (stringValue(dateStr).length() == 0 || stringValue(pattern).length() == 0) return null;
        try {
            return new SimpleDateFormat(pattern).parse(dateStr.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    public static String formatDate(Date date, String pattern) {
        if (date == null || stringValue(pattern).length() == 0) return "";
        try {
            return new SimpleDateFormat(pattern).format(date);
        } catch (Exception e) {
            return "";
        }
    }

    public static String formatLocalDate(LocalDate date, String pattern) {
        if (date == null || stringValue(pattern).length() == 0) return "";
        try {
            return date.format(DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            return "";
        }
    }

    public static LocalDate parseLocalDate(String dateStr, String pattern) {
        if (stringValue(dateStr).length() == 0 || stringValue(pattern).length() == 0) return null;
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static LocalDateTime parseLocalDateTime(String dateStr, String pattern) {
        if (stringValue(dateStr).length() == 0 || stringValue(pattern).length() == 0) return null;
        try {
            return LocalDateTime.parse(dateStr.trim(), DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static long daysBetween(Date toDate, Date fromDate) {
        if (toDate == null || fromDate == null) return 0;
        long diff = toDate.getTime() - fromDate.getTime();
        return diff / (24 * 60 * 60 * 1000);
    }

    public static String getTimeWithoutSecond(String time) {
        if (stringValue(time).length() == 0) return "";
        try {
            Date d = new SimpleDateFormat("HH:mm:ss").parse(time);
            return new SimpleDateFormat("HH:mm").format(d);
        } catch (ParseException e) {
            return stringValue(time);
        }
    }

    public static String getDateTimeObjectInIndianFormat(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat(INDIAN_DATETIME_PATTERN).format(date);
    }
}
