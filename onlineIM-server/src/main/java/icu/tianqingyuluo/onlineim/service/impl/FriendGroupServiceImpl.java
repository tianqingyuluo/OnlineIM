package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendGroupResponse;
import icu.tianqingyuluo.onlineim.service.FriendGroupService;

import java.util.List;
import java.util.Map;

public class FriendGroupServiceImpl implements FriendGroupService {
    @Override
    public List<FriendGroupResponse> getFriendGroupsByUserId(String userId) throws Exception {
        return List.of();
    }

    @Override
    public FriendGroupResponse createFriendGroup(String userId, FriendGroupCreateRequest request) throws Exception {
        return null;
    }

    @Override
    public FriendGroupResponse updateFriendGroup(String userId, String groupId, FriendGroupUpdateRequest request) throws Exception {
        return null;
    }

    @Override
    public boolean deleteFriendGroup(String userId, String groupId) throws Exception {
        return false;
    }

    @Override
    public boolean sortFriendGroups(String userId, List<Map<String, Object>> request) throws Exception {
        return false;
    }
}
