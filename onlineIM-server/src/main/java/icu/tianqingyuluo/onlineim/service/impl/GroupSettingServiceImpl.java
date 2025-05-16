package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupSettingUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupSettingResponse;
import icu.tianqingyuluo.onlineim.service.GroupSettingService;

public class GroupSettingServiceImpl implements GroupSettingService {
    @Override
    public GroupSettingResponse getGroupSettings(String groupId, String operatorId) {
        return null;
    }

    @Override
    public GroupSettingResponse updateGroupSettings(String groupId, GroupSettingUpdateRequest request, String operatorId) {
        return null;
    }

    @Override
    public GroupSettingResponse muteAll(String groupId, String operatorId) {
        return null;
    }

    @Override
    public GroupSettingResponse unmuteAll(String groupId, String operatorId) {
        return null;
    }
}
