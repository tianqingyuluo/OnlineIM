package icu.tianqingyuluo.onlineim.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 多端登录的设备类型。同一用户每种类型同时只保留一个会话。
 */
public final class DeviceTypes {

    public static final String WEB = "web";
    public static final String DESKTOP = "desktop";
    public static final String PHONE = "phone";

    /** 同类型的另一台设备新登录 */
    public static final String REASON_SAME_TYPE = "SAME_DEVICE_TYPE_LOGIN";
    /** 同一设备重新登录，旧连接作废 */
    public static final String REASON_RELOGIN = "SESSION_REPLACED";
    /** 用户在设备列表里远程登出 */
    public static final String REASON_REMOTE = "REMOTE_LOGOUT";

    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private DeviceTypes() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEB;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "desktop", "pc", "tauri", "windows", "macos", "linux" -> DESKTOP;
            case "phone", "mobile", "android", "ios", "tablet", "wearable" -> PHONE;
            default -> WEB;
        };
    }

    public static String label(String deviceType) {
        return switch (normalize(deviceType)) {
            case DESKTOP -> "桌面端";
            case PHONE -> "手机端";
            default -> "网页端";
        };
    }

    public static String kickMessage(String reason, String deviceType) {
        if (REASON_REMOTE.equals(reason)) {
            return "该设备已被远程登出";
        }
        if (REASON_RELOGIN.equals(reason)) {
            return "您已在此设备重新登录，当前页面已下线";
        }
        return "您的账号已在其他" + label(deviceType) + "登录，当前设备已下线";
    }

    /**
     * 只接受可放进 Redis key 的设备标识；不合法时返回 null，由调用方生成新的。
     */
    public static String sanitizeDeviceId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return DEVICE_ID.matcher(trimmed).matches() ? trimmed : null;
    }

    public static boolean isSafeDeviceId(String raw) {
        return sanitizeDeviceId(raw) != null;
    }
}
