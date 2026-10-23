package icu.tianqingyuluo.onlineim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceNamesTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String EDGE_WINDOWS = CHROME_WINDOWS + " Edg/120.0.0.0";
    private static final String IPHONE_SAFARI =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String ANDROID_CHROME =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String FIREFOX_LINUX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0";

    @Test
    void namesDesktopAndPhoneFromUserAgent() {
        assertEquals("桌面端 · Chrome / Windows",
                DeviceNames.resolve(null, CHROME_WINDOWS, DeviceTypes.DESKTOP, "desktop-1"));
        assertEquals("桌面端 · Edge / Windows",
                DeviceNames.fromUserAgent(EDGE_WINDOWS, DeviceTypes.DESKTOP));
        assertEquals("手机端 · Safari / iOS",
                DeviceNames.resolve("  ", IPHONE_SAFARI, DeviceTypes.PHONE, "phone-1"));
        assertEquals("手机端 · Chrome / Android",
                DeviceNames.fromUserAgent(ANDROID_CHROME, DeviceTypes.PHONE));
        assertEquals("网页端 · Firefox / Linux",
                DeviceNames.fromUserAgent(FIREFOX_LINUX, DeviceTypes.WEB));
    }

    @Test
    void prefersClientNameAndIgnoresOpaqueDeviceId() {
        assertEquals("我的手机",
                DeviceNames.resolve("  我的手机  ", IPHONE_SAFARI, DeviceTypes.PHONE, "phone-1"));
        assertEquals("桌面端 · Chrome / Windows",
                DeviceNames.resolve("desktop-1", CHROME_WINDOWS, DeviceTypes.DESKTOP, "desktop-1"));
        assertEquals("桌面端", DeviceNames.resolve(null, null, DeviceTypes.DESKTOP, "desktop-1"));
        assertEquals("网页端", DeviceNames.fromUserAgent("Mozilla/5.0", DeviceTypes.WEB));
    }

    @Test
    void stripsControlsAndTruncates() {
        assertNull(DeviceNames.sanitize(" \n\t "));
        assertEquals("书房电脑", DeviceNames.sanitize("书房\u0007电脑"));
        String longName = "名".repeat(80);
        assertEquals(DeviceNames.MAX_LENGTH, DeviceNames.sanitize(longName).length());
        assertTrue(DeviceNames.sanitize(longName).startsWith("名"));
    }
}
