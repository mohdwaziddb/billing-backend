package com.billing.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Browser ke User-Agent se padhne layak device naam ("Chrome on Windows",
 * "Safari on iPhone"). Bina library ke: sirf aam browsers / OS pehchanta hai,
 * baaki "Unknown" — ye sirf dikhane ke liye hai, security ke liye nahi.
 */
public final class UserAgentParser {

    public static final String DESKTOP = "Desktop";
    public static final String MOBILE = "Mobile";
    public static final String TABLET = "Tablet";

    private UserAgentParser() {
    }

    public record Device(String browser, String os, String deviceType) {
        public String displayName() {
            String browserName = browser.replaceAll(" \\d+$", "");
            String osName = os.startsWith("iOS") && MOBILE.equals(deviceType) ? "iPhone" : os.replaceAll(" [\\d.]+$", "");
            if ("Unknown".equals(browserName) && "Unknown".equals(osName)) {
                return "Unknown device";
            }
            return browserName + " on " + osName;
        }
    }

    public static Device parse(String userAgent) {
        String ua = userAgent == null ? "" : userAgent;
        return new Device(browser(ua), os(ua), deviceType(ua));
    }

    private static String browser(String ua) {
        // Order zaroori: Edge/Opera/Samsung ke UA me "Chrome" bhi hota hai, Chrome me "Safari".
        String[][] rules = {
                {"Edg(?:e|A|iOS)?/(\\d+)", "Edge"},
                {"OPR/(\\d+)", "Opera"},
                {"SamsungBrowser/(\\d+)", "Samsung Internet"},
                {"CriOS/(\\d+)", "Chrome"},
                {"FxiOS/(\\d+)", "Firefox"},
                {"Firefox/(\\d+)", "Firefox"},
                {"Chrome/(\\d+)", "Chrome"},
                {"Version/(\\d+)[^ ]* (?:Mobile/\\S+ )?Safari/", "Safari"},
        };
        for (String[] rule : rules) {
            Matcher matcher = Pattern.compile(rule[0]).matcher(ua);
            if (matcher.find()) {
                return rule[1] + " " + matcher.group(1);
            }
        }
        return "Unknown";
    }

    private static String os(String ua) {
        Matcher android = Pattern.compile("Android (\\d+)").matcher(ua);
        if (android.find()) {
            return "Android " + android.group(1);
        }
        Matcher ios = Pattern.compile("(?:iPhone|CPU) OS (\\d+)").matcher(ua);
        if (ios.find()) {
            return (ua.contains("iPad") ? "iPadOS " : "iOS ") + ios.group(1);
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return "Unknown";
    }

    private static String deviceType(String ua) {
        String lower = ua.toLowerCase(Locale.ROOT);
        if (lower.contains("ipad") || lower.contains("tablet") || (lower.contains("android") && !lower.contains("mobile"))) {
            return TABLET;
        }
        if (lower.contains("mobi") || lower.contains("iphone") || lower.contains("android")) {
            return MOBILE;
        }
        return DESKTOP;
    }
}
