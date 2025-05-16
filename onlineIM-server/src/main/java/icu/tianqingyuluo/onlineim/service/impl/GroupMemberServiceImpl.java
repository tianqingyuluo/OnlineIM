package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;

public class GroupMemberServiceImpl implements GroupMemberService {

    @Override
    public GroupMemberResponse getMemberDetail(String groupId, String memberId, String operatorId) {
        return null;
    }

    @Override
    public boolean removeMember(String groupId, String memberId, String operatorId) {
        return false;
    }

    @Override
    public boolean setAdmin(String groupId, String memberId, String operatorId) {
        return false;
    }

    @Override
    public boolean cancelAdmin(String groupId, String memberId, String operatorId) {
        return false;
    }

    @Override
    public boolean updateMemberNickname(String groupId, String memberId, String nickname, String operatorId) {
        return false;
    }

    @Override
    public boolean muteMember(String groupId, String memberId, Integer duration, String operatorId) {
        return false;
    }

    @Override
    public boolean unmuteMember(String groupId, String memberId, String operatorId) {
        return false;
    }

    @Override
    public boolean transferOwnership(String groupId, String memberId, String operatorId) {
        return false;
    }
}
