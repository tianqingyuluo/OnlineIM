package icu.tianqingyuluo.onlineim.util;

/**
 * 会话 ID 生成工具。
 */
public final class ConversationIdUtil {

    private ConversationIdUtil() {
    }

    /**
     * 按用户 ID 字典序生成双方一致的私聊会话 ID。
     */
    public static String privateConversationId(String firstUserId, String secondUserId) {
        if (firstUserId == null || firstUserId.isBlank()
                || secondUserId == null || secondUserId.isBlank()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return "conv_" + (firstUserId.compareTo(secondUserId) <= 0
                ? firstUserId + secondUserId
                : secondUserId + firstUserId);
    }
}
