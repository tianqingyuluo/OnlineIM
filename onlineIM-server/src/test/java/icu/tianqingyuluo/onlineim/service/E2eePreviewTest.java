package icu.tianqingyuluo.onlineim.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class E2eePreviewTest {

    @Test
    void conversationPreviewMasksCiphertext() {
        String envelope = "{\"e2ee\":true,\"ciphertext\":\"秘密计划AAAA\",\"iv\":\"abcd\",\"sender_key_id\":\"k\"}";
        assertEquals("[加密消息]", ConversationRecordService.preview("text", envelope));
        assertEquals("明文还在", ConversationRecordService.preview("text", "明文还在"));
        assertEquals("[图片]", ConversationRecordService.preview("image", "upl_file"));
    }
}
