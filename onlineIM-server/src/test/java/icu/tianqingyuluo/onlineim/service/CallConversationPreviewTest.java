package icu.tianqingyuluo.onlineim.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallConversationPreviewTest {

    @Test
    void callMessagesUseStablePreviews() {
        assertEquals("[视频通话] 01:23", ConversationRecordService.preview("call",
                "{\"call_id\":\"call_1\",\"call_type\":\"video\",\"call_status\":\"completed\",\"duration_seconds\":83}"));
        assertEquals("[语音通话] 00:45", ConversationRecordService.preview("call",
                "{\"call_type\":\"audio\",\"call_status\":\"completed\",\"duration_seconds\":45}"));
        assertEquals("[未接视频通话]", ConversationRecordService.preview("call",
                "{\"call_type\":\"video\",\"call_status\":\"missed\",\"duration_seconds\":0}"));
        assertEquals("[未接语音通话]", ConversationRecordService.preview("call",
                "{\"call_type\":\"audio\",\"call_status\":\"missed\"}"));
        assertEquals("[已拒接]", ConversationRecordService.preview("call",
                "{\"call_type\":\"video\",\"call_status\":\"rejected\"}"));
        assertEquals("[已取消]", ConversationRecordService.preview("call",
                "{\"call_type\":\"audio\",\"call_status\":\"cancelled\"}"));
        assertEquals("[对方忙线]", ConversationRecordService.preview("call",
                "{\"call_type\":\"video\",\"call_status\":\"busy\"}"));
    }
}
