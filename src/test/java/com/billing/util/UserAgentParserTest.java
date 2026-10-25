package com.billing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Asli browsers ke User-Agent se device ka naam. */
class UserAgentParserTest {

    private static void expect(String ua, String name, String type, String browser, String os) {
        UserAgentParser.Device device = UserAgentParser.parse(ua);
        assertEquals(name, device.displayName(), ua);
        assertEquals(type, device.deviceType(), ua);
        assertEquals(browser, device.browser(), ua);
        assertEquals(os, device.os(), ua);
    }

    @Test
    void desktopBrowsers() {
        expect("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
                "Chrome on Windows", "Desktop", "Chrome 129", "Windows");
        expect("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.2792.79",
                "Edge on Windows", "Desktop", "Edge 129", "Windows");
        expect("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
                "Safari on macOS", "Desktop", "Safari 17", "macOS");
        expect("Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0",
                "Firefox on Linux", "Desktop", "Firefox 131", "Linux");
    }

    @Test
    void phonesAndTablets() {
        expect("Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
                "Chrome on Android", "Mobile", "Chrome 129", "Android 14");
        expect("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
                "Safari on iPhone", "Mobile", "Safari 17", "iOS 17");
        expect("Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/129.0.6668.69 Mobile/15E148 Safari/604.1",
                "Chrome on iPadOS", "Tablet", "Chrome 129", "iPadOS 17");
        expect("Mozilla/5.0 (Linux; Android 13; SAMSUNG SM-X200) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/25.0 Chrome/121.0.0.0 Safari/537.36",
                "Samsung Internet on Android", "Tablet", "Samsung Internet 25", "Android 13");
    }

    @Test
    void unknownClient() {
        expect(null, "Unknown device", "Desktop", "Unknown", "Unknown");
    }
}
