package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.mapper.FriendRequestMapper;

import icu.tianqingyuluo.onlineim.mapper.FriendRequestResponseMapper;
import icu.tianqingyuluo.onlineim.mapper.UserFriendMapper;
import icu.tianqingyuluo.onlineim.pojo.entity.UserFriend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendServiceImplTest {

    private UserFriendMapper userFriendMapper;
    private FriendRequestResponseMapper friendRequestResponseMapper;
    private FriendRequestMapper friendRequestMapper;
    private FriendServiceImpl friendService;

    @BeforeEach
    void setUp() {
        userFriendMapper = mock(UserFriendMapper.class);
        friendRequestResponseMapper = mock(FriendRequestResponseMapper.class);
        friendRequestMapper = mock(FriendRequestMapper.class);
        friendService = new FriendServiceImpl(null, null, friendRequestResponseMapper, userFriendMapper, friendRequestMapper);
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

    @Test
    void resolvesRelationshipIdOrFriendUserId() {
        when(friendRequestResponseMapper.existFriendByID("rel_1", "usr_me")).thenReturn(true);
        assertEquals("rel_1", friendService.resolveOwnedFriendshipId("usr_me", "rel_1"));

        when(friendRequestResponseMapper.existFriendByID("usr_friend", "usr_me")).thenReturn(false);
        when(userFriendMapper.getByUserIdAndFriendId("usr_me", "usr_friend"))
                .thenReturn(UserFriend.builder().id("rel_2").status(1).build());
        assertEquals("rel_2", friendService.resolveOwnedFriendshipId("usr_me", "usr_friend"));

        when(friendRequestResponseMapper.existFriendByID("usr_missing", "usr_me")).thenReturn(false);
        when(userFriendMapper.getByUserIdAndFriendId("usr_me", "usr_missing")).thenReturn(null);
        assertNull(friendService.resolveOwnedFriendshipId("usr_me", "usr_missing"));
        assertNull(friendService.resolveOwnedFriendshipId("usr_me", " "));
    }

    @Test
    void deleteFriendRemovesBothDirectionsAndRequests() {
        when(userFriendMapper.getByUserIdAndFriendId("usr_a", "usr_b"))
                .thenReturn(UserFriend.builder().id("rel_ab").status(1).build());
        when(userFriendMapper.getFriendIdByIdAndUserId("rel_ab", "usr_a")).thenReturn("usr_b");

        assertEquals("usr_b", friendService.deleteFriend("usr_a", "usr_b"));

        verify(userFriendMapper).deleteByUserIdAndFriendId("usr_a", "usr_b");
        verify(userFriendMapper).deleteByUserIdAndFriendId("usr_b", "usr_a");
        verify(friendRequestMapper).deleteBetween("usr_a", "usr_b");
    }

    @Test
    void deleteFriendIgnoresRelationsOwnedBySomeoneElse() {
        assertNull(friendService.deleteFriend("usr_c", "usr_b"));

        verify(userFriendMapper, never()).deleteByUserIdAndFriendId(anyString(), anyString());
        verify(friendRequestMapper, never()).deleteBetween(anyString(), anyString());
    }
}
