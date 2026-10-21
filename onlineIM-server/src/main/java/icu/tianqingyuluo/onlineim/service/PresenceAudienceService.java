package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.mapper.GroupMemberMapper;
import icu.tianqingyuluo.onlineim.mapper.UserFriendMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 谁可以看见一个用户的在线状态：互为好友，或仍在同一个未解散的群里。
 */
@Service
public class PresenceAudienceService {

    private final UserFriendMapper userFriendMapper;
    private final GroupMemberMapper groupMemberMapper;

    public PresenceAudienceService(UserFriendMapper userFriendMapper, GroupMemberMapper groupMemberMapper) {
        this.userFriendMapper = userFriendMapper;
        this.groupMemberMapper = groupMemberMapper;
    }

    /** 状态变化时要推送的人，不含本人。 */
    public List<String> watchers(String userId) {
        Set<String> ids = related(userId);
        ids.remove(userId);
        return List.copyOf(ids);
    }

    /** targetIds 中，viewer 有权看见的那些。不含“自己看自己”。 */
    public Set<String> visibleTargets(String viewerId, Collection<String> targetIds) {
        if (viewerId == null || targetIds == null || targetIds.isEmpty()) {
            return Set.of();
        }
        Set<String> allowed = related(viewerId);
        Set<String> visible = new HashSet<>();
        for (String targetId : targetIds) {
            if (targetId != null && allowed.contains(targetId)) {
                visible.add(targetId);
            }
        }
        return visible;
    }

    private Set<String> related(String userId) {
        Set<String> ids = new LinkedHashSet<>();
        if (userId == null || userId.isBlank()) {
            return ids;
        }
        List<String> friends = userFriendMapper.listMutualFriendIds(userId);
        if (friends != null) {
            ids.addAll(friends);
        }
        List<String> members = groupMemberMapper.listCoMemberIds(userId);
        if (members != null) {
            ids.addAll(members);
        }
        return ids;
    }
}
