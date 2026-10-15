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


    // ========== Collection helpers ==========


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


    // ========== Date / Time ==========

    private static final String US_DATE_PATTERN = "yyyy-MM-dd";
    private static final String INDIAN_DATE_PATTERN = "dd-MM-yyyy";
    private static final String US_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String INDIAN_DATETIME_PATTERN = "dd-MM-yyyy HH:mm:ss";
    private static final String INDIAN_DATETIME_SLASH_PATTERN = "dd/MM/yyyy HH:mm:ss";


    public static String getCurrentDateInUSFormat() {
        return new SimpleDateFormat(US_DATE_PATTERN).format(new Date());
    }

    public static String getCurrentDateInIndianFormat() {
        return new SimpleDateFormat(INDIAN_DATE_PATTERN).format(new Date());
    }


    public static LocalDate parseLocalDate(String dateStr, String pattern) {
        if (stringValue(dateStr).length() == 0 || stringValue(pattern).length() == 0) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Flexible date parse shared by all filter surfaces: tries yyyy-MM-dd
     * first, then dd-MM-yyyy. Single place for the format fallback every
     * service used to copy-paste.
     */
    public static LocalDate parseFlexibleDate(Object value) {
        String text = stringValue(value);
        if (text.isEmpty()) {
            return null;
        }
        LocalDate parsed = parseLocalDate(text, "yyyy-MM-dd");
        if (parsed == null) {
            parsed = parseLocalDate(text, "dd-MM-yyyy");
        }
        return parsed;
    }

    /**
     * Same fallback across alternate param keys (e.g. startDate/start_date):
     * tries every key in order, both formats each.
     */
    public static LocalDate parseFlexibleDate(Map<String, Object> param, String... keys) {
        if (param == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            LocalDate parsed = parseFlexibleDate(param.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }
}
