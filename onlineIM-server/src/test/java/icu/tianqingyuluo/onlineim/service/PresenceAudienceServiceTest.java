package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.mapper.GroupMemberMapper;
import icu.tianqingyuluo.onlineim.mapper.UserFriendMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresenceAudienceServiceTest {

    private final UserFriendMapper userFriendMapper = mock(UserFriendMapper.class);
    private final GroupMemberMapper groupMemberMapper = mock(GroupMemberMapper.class);
    private final PresenceAudienceService audience = new PresenceAudienceService(userFriendMapper, groupMemberMapper);

    @Test
    void watchersAreMutualFriendsAndActiveCoMembersWithoutSelf() {
        when(userFriendMapper.listMutualFriendIds("usr_a")).thenReturn(List.of("usr_b"));
        when(groupMemberMapper.listCoMemberIds("usr_a")).thenReturn(List.of("usr_c", "usr_b"));

        assertEquals(List.of("usr_b", "usr_c"), audience.watchers("usr_a"));
    }

    @Test
    void oneWayRelationIsNotVisibleUnlessTheyShareAGroup() {
        when(userFriendMapper.listMutualFriendIds("usr_b")).thenReturn(List.of());
        when(groupMemberMapper.listCoMemberIds("usr_b")).thenReturn(List.of("usr_d"));

        assertTrue(audience.visibleTargets("usr_b", List.of("usr_a", "usr_d")).equals(Set.of("usr_d")));
        assertTrue(audience.watchers("usr_nobody").isEmpty());
    }
}
