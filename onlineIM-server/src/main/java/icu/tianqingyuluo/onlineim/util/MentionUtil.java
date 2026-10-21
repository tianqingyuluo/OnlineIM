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
 * <p>@所有人使用保留值 {@link #MENTION_ALL}（不展开成成员 ID，因此不受 50 人上限）。
 * 只有群主/管理员（调用方传入 {@code canMentionAll}）能留下这个值，其他人的 {@code all} 会被丢掉。</p>
 */
public final class MentionUtil {

    public static final int MAX_MENTIONS = 50;

    /** 与前端 MENTION_ALL_ID 一致：at_users 里表示 @所有人，不是某个用户的 ID */
    public static final String MENTION_ALL = "all";

    private MentionUtil() {
    }

    /** 返回清洗后的列表；没有有效提及时返回 null（不落库空数组）。默认不允许 @所有人。 */
    public static List<String> sanitize(String messageType, String senderId,
                                        Collection<String> requested, Collection<String> memberIds) {
        return sanitize(messageType, senderId, requested, memberIds, false);
    }

    /**
     * @param canMentionAll 发送者当前是群主或管理员时为 true，此时保留 {@link #MENTION_ALL}
     */
    public static List<String> sanitize(String messageType, String senderId,
                                        Collection<String> requested, Collection<String> memberIds,
                                        boolean canMentionAll) {
        if (requested == null || requested.isEmpty() || memberIds == null
                || (messageType != null && !"text".equalsIgnoreCase(messageType))) {
            return null;
        }
        Set<String> members = new LinkedHashSet<>(memberIds);
        Set<String> people = new LinkedHashSet<>();
        boolean mentionAll = false;
        for (String id : requested) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (MENTION_ALL.equals(id)) {
                mentionAll = true;
                continue;
            }
            if (id.equals(senderId) || !members.contains(id) || people.size() >= MAX_MENTIONS) {
                continue;
            }
            people.add(id);
        }
        if (!mentionAll || !canMentionAll) {
            return people.isEmpty() ? null : new ArrayList<>(people);
        }
        List<String> result = new ArrayList<>(people.size() + 1);
        result.add(MENTION_ALL);
        result.addAll(people);
        return result;
    }

    public static boolean mentionsAll(Collection<String> atUsers) {
        return atUsers != null && atUsers.contains(MENTION_ALL);
    }

    /** 该用户是否被这条消息 @ 到（含 @所有人）。发送者本人不算。 */
    public static boolean mentions(Collection<String> atUsers, String userId) {
        if (atUsers == null || userId == null || userId.isBlank()) {
            return false;
        }
        return mentionsAll(atUsers) || atUsers.contains(userId);
    }

    /**
     * 需要打上“有人@我”的成员：含 {@link #MENTION_ALL} 时是除发送者外的全体当前成员，
     * 否则是 at_users 里仍在群里的那些人（同样不含发送者）。
     */
    public static Set<String> notifiedUserIds(String senderId, Collection<String> atUsers, Collection<String> memberIds) {
        Set<String> members = new LinkedHashSet<>();
        if (memberIds != null) {
            for (String id : memberIds) {
                if (id != null && !id.isBlank()) {
                    members.add(id);
                }
            }
        }
        Set<String> result = new LinkedHashSet<>();
        if (mentionsAll(atUsers)) {
            for (String id : members) {
                if (!id.equals(senderId)) {
                    result.add(id);
                }
            }
        }
        if (atUsers != null) {
            for (String id : atUsers) {
                if (id == null || id.isBlank() || MENTION_ALL.equals(id) || id.equals(senderId)) {
                    continue;
                }
                if (members.contains(id)) {
                    result.add(id);
                }
            }
        }
        return result;
    }
}
