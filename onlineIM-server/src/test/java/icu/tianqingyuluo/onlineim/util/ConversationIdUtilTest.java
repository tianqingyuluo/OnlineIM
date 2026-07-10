package icu.tianqingyuluo.onlineim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversationIdUtilTest {

    @Test
    void generatesSamePrivateConversationIdForBothDirections() {
        assertEquals("conv_usr_ausr_b", ConversationIdUtil.privateConversationId("usr_a", "usr_b"));
        assertEquals("conv_usr_ausr_b", ConversationIdUtil.privateConversationId("usr_b", "usr_a"));
    }

    @Test
    void rejectsBlankUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> ConversationIdUtil.privateConversationId("usr_a", " "));
    }
}
