package icu.tianqingyuluo.onlineim.util;

import java.util.Locale;

/**
 * 登录设备在列表里的可读名称。优先用客户端传来的名称，否则用 User-Agent 里的浏览器和系统，最后退回设备类型。
 */
public final class DeviceNames {

    public static final int MAX_LENGTH = 64;

    private DeviceNames() {
    }

    /**
     * @param deviceId 已经清洗过的设备标识；客户端若把标识本身当成名称，则忽略，避免列表里只剩一串 id
     */
    public static String resolve(String clientName, String userAgent, String deviceType, String deviceId) {
        String supplied = sanitize(clientName);
        if (supplied != null && supplied.equals(deviceId)) {
            supplied = null;
        }
        if (supplied != null) {
            return supplied;
        }
        return fromUserAgent(userAgent, deviceType);
    }

    public static String fromUserAgent(String userAgent, String deviceType) {
        String label = DeviceTypes.label(deviceType);
        if (userAgent == null || userAgent.isBlank()) {
            return label;
        }
        String sample = userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
        String lower = sample.toLowerCase(Locale.ROOT);
        String browser = browserOf(lower);
        String os = osOf(lower);
        if (browser != null && os != null) {
            return limit(label + " · " + browser + " / " + os);
        }
        if (browser != null || os != null) {
            return limit(label + " · " + (browser != null ? browser : os));
        }
        return label;
    }

    /**
     * 去掉控制字符和多余空白，并截断到 {@link #MAX_LENGTH}。空白或 null 返回 null。
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[\\p{Cntrl}]", "").strip().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return null;
        }
        return limit(cleaned);
    }

    private static String limit(String value) {
        if (value.length() <= MAX_LENGTH) {
            return value;
        }
        String truncated = value.substring(0, MAX_LENGTH).strip();
        return truncated.isEmpty() ? null : truncated;
    }

    private static String browserOf(String ua) {
        if (ua.contains("micromessenger")) {
            return "微信";
        }
        if (ua.contains("edg/") || ua.contains("edge/")) {
            return "Edge";
        }
        if (ua.contains("opr/") || ua.contains("opera")) {
            return "Opera";
        }
        if (ua.contains("firefox/") || ua.contains("fxios/")) {
            return "Firefox";
        }
        if (ua.contains("chromium/")) {
            return "Chromium";
        }
        if (ua.contains("chrome/") || ua.contains("crios/")) {
            return "Chrome";
        }
        if (ua.contains("safari/")) {
            return "Safari";
        }
        return null;
    }

    private static String osOf(String ua) {
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            return "iOS";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("cros")) {
            return "ChromeOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return null;
    }
}
