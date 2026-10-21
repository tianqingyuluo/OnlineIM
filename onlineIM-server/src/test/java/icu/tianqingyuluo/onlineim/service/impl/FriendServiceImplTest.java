package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.mapper.UserFriendMapper;
import icu.tianqingyuluo.onlineim.pojo.entity.UserFriend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FriendServiceImplTest {

    private UserFriendMapper userFriendMapper;
    private FriendServiceImpl friendService;

    @BeforeEach
    void setUp() {
        userFriendMapper = mock(UserFriendMapper.class);
        friendService = new FriendServiceImpl(null, null, null, userFriendMapper, null);
    }

    @Test
    void bothNormalDirectionsCountAsFriends() {
        when(userFriendMapper.getByUserIdAndFriendId("usr_a", "usr_b"))
                .thenReturn(UserFriend.builder().status(1).build());
        when(userFriendMapper.getByUserIdAndFriendId("usr_b", "usr_a"))
                .thenReturn(UserFriend.builder().status(1).build());

        assertTrue(friendService.areFriends("usr_a", "usr_b"));
    }

    @Test
    void blockedOrOneWayRelationIsNotFriendship() {
        when(userFriendMapper.getByUserIdAndFriendId("usr_a", "usr_b"))
                .thenReturn(UserFriend.builder().status(1).build());
        when(userFriendMapper.getByUserIdAndFriendId("usr_b", "usr_a"))
                .thenReturn(UserFriend.builder().status(2).build());

        assertFalse(friendService.areFriends("usr_a", "usr_b"));
        assertFalse(friendService.areFriends("usr_a", "usr_a"));
        assertFalse(friendService.areFriends(null, "usr_b"));
    }
}
