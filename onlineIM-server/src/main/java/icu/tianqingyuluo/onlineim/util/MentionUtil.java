package icu.tianqingyuluo.onlineim.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 群聊 @提及：服务端以结构化的 {@code at_users}（用户 ID 列表）为准，正文里的 “@昵称” 只用于展示。
 *
 * <p>清洗规则：只有群聊文本消息可以 @；去重、去空；只保留发送时仍在群里的成员；不能 @ 自己；
 * 最多 {@link #MAX_MENTIONS} 人，多余的丢弃。非法项直接丢弃而不是拒绝整条消息。</p>
 *
 * <p>@全员（见 v1-group-at-all-announce-read）尚未实现：届时在这里识别保留标记（如 {@code "all"}）
 * 并做权限 / 频次校验；目前 {@code "all"} 不是合法的用户 ID，会被当作非成员丢弃。</p>
 */
public final class MentionUtil {

    public static final int MAX_MENTIONS = 50;

    private MentionUtil() {
    }

    /** 返回清洗后的列表；没有有效提及时返回 null（不落库空数组） */
    public static List<String> sanitize(String messageType, String senderId,
                                        Collection<String> requested, Collection<String> memberIds) {
        if (requested == null || requested.isEmpty() || memberIds == null
                || (messageType != null && !"text".equalsIgnoreCase(messageType))) {
            return null;
        }
        Set<String> members = new LinkedHashSet<>(memberIds);
        Set<String> result = new LinkedHashSet<>();
        for (String id : requested) {
            if (id == null || id.isBlank() || id.equals(senderId) || !members.contains(id)) {
                continue;
            }
            result.add(id);
            if (result.size() >= MAX_MENTIONS) {
                break;
            }
        }
        return result.isEmpty() ? null : new ArrayList<>(result);
    }
}
