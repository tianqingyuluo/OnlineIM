package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendResponse;

import java.util.List;

public interface FriendResponseMapper {
    List<FriendResponse> getFriendListByUsername(String username);
    FriendResponse getFriendByID(String id);
}
