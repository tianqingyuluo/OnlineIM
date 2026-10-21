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

    /**
     * 从私聊会话 ID 里取出对方的用户 ID。ID 不是 {@code conv_} 加两个 {@code usr_} 用户、
     * 或不包含 {@code userId} 时返回 null。
     */
    public static String otherParticipant(String conversationId, String userId) {
        if (conversationId == null || userId == null || !conversationId.startsWith("conv_")) {
            return null;
        }
        String[] parts = conversationId.substring("conv_".length()).split("usr_", -1);
        if (parts.length != 3 || parts[1].isEmpty() || parts[2].isEmpty()) {
            return null;
        }
        String first = "usr_" + parts[1];
        String second = "usr_" + parts[2];
        if (userId.equals(first)) {
            return second;
        }
        if (userId.equals(second)) {
            return first;
        }
        return null;
    }
}
