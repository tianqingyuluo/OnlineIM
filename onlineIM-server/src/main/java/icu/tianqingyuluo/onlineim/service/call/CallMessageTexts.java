package icu.tianqingyuluo.onlineim.service.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 通话记录在会话列表和引用里的预览文案。Web / Tauri / Flutter 使用同一组中文。
 */
public final class CallMessageTexts {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CallMessageTexts() {
    }

    public static String formatDuration(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("%02d:%02d", safe / 60, safe % 60);
    }

    public static String preview(String content) {
        Parsed parsed = parse(content);
        String type = parsed.callType == null ? "" : parsed.callType;
        return switch (parsed.status == null ? "" : parsed.status) {
            case CallFrames.RECORD_COMPLETED ->
                    (CallFrames.TYPE_AUDIO.equals(type) ? "[语音通话] " : "[视频通话] ") + formatDuration(parsed.durationSeconds);
            case CallFrames.RECORD_REJECTED -> "[已拒接]";
            case CallFrames.RECORD_CANCELLED -> "[已取消]";
            case CallFrames.RECORD_BUSY -> "[对方忙线]";
            case CallFrames.RECORD_MISSED ->
                    CallFrames.TYPE_AUDIO.equals(type) ? "[未接语音通话]" : "[未接视频通话]";
            default -> CallFrames.TYPE_AUDIO.equals(type) ? "[语音通话]" : "[视频通话]";
        };
    }

    public static Parsed parse(String content) {
        Parsed parsed = new Parsed();
        if (content == null || content.isBlank() || !content.trim().startsWith("{")) {
            return parsed;
        }
        try {
            JsonNode node = MAPPER.readTree(content);
            parsed.callType = text(node, "call_type");
            parsed.status = text(node, "call_status");
            if (node.has("duration_seconds") && node.get("duration_seconds").canConvertToInt()) {
                parsed.durationSeconds = node.get("duration_seconds").asInt();
            }
        } catch (Exception ignored) {
            return new Parsed();
        }
        return parsed;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    public static final class Parsed {
        public String callType;
        public String status;
        public int durationSeconds;
    }
}
