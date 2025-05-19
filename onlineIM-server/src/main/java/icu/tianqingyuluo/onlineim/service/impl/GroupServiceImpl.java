package icu.tianqingyuluo.onlineim.service.impl;

import cn.hutool.core.util.IdUtil;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.mapper.*;
import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupJoinRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.GroupJoinRequest;
import icu.tianqingyuluo.onlineim.service.GroupService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class GroupServiceImpl implements GroupService {

    private final GroupMapper groupMapper;
    private final GroupResponseMapper groupResponseMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupSettingMapper groupSettingMapper;
    private final UserMapper userMapper;
    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final GroupBriefResponseMapper groupBriefResponseMapper;
    private final GroupJoinRequestResponseMapper groupJoinRequestResponseMapper;

    public GroupServiceImpl(GroupMapper groupMapper, GroupResponseMapper groupResponseMapper, GroupMemberMapper groupMemberMapper, GroupSettingMapper groupSettingMapper, UserMapper userMapper, GroupJoinRequestMapper groupJoinRequestMapper, GroupBriefResponseMapper groupBriefResponseMapper,GroupJoinRequestResponseMapper groupJoinRequestResponseMapper) {
        this.groupMapper = groupMapper;
        this.groupResponseMapper = groupResponseMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.groupSettingMapper = groupSettingMapper;
        this.userMapper = userMapper;
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.groupBriefResponseMapper = groupBriefResponseMapper;
        this.groupJoinRequestResponseMapper = groupJoinRequestResponseMapper;
    }

    @Override
    @Transactional
    public String createGroup(GroupCreateRequest request, String userID) {
        String gid="grp_" + IdUtil.getSnowflakeNextIdStr();
        String sid="set_"+ IdUtil.getSnowflakeNextIdStr();
        int current_members = request.getMemberIDs().size();
        groupMapper.insertGroup(gid,userID,request,current_members);
        groupMapper.insertGroupMember(gid,request.getMemberIDs());
        groupMemberMapper.initGroupOwner(userID);
        groupSettingMapper.initSettings(sid,gid);
        return gid;
    }

    @Override
    public GroupBriefResponse getGroupByID(String groupID) {
        return groupMapper.getById(groupID);
    }

    @Override
        public GroupResponse getGroupDetailByID(String groupId,String userid) {
        return groupResponseMapper.getGroupDetailByID(groupId,userid);
    }

    @Override
    @Transactional
    public GroupResponse updateGroupByID(String groupId, GroupUpdateRequest request, String userID) {
        groupMapper.update(groupId,request);
        return groupResponseMapper.getGroupDetailByID(groupId,userID);
    }

    @Override
    @Transactional
    public boolean disbandGroupByID(String groupId, String userid) {
        if(groupMapper.isGroupOwner(groupId,userid)){
            groupMapper.deleteGroup(groupId);
            groupMapper.deleteMembers(groupId);
            groupMapper.deleteAnnouncements(groupId);
            return true;
        }
        return false;
    }

    @Override
    public List<GroupResponse> getJoinedGroupsByUserID(String userid) {
        return groupResponseMapper.getJoinedGroupsByUserID(userid);
    }

    @Override
    public boolean isUserAdminOrOwner(String groupId, String userid) {
        return groupMapper.isGroupOwner(groupId,userid);
    }

    @Override
    public String uploadGroupAvatar(String groupId, MultipartFile avatar, String userid) {
        return "";
    }

    @Override
    public List<GroupMemberResponse> getGroupMembersByGroupID(String groupId) {
        return groupMemberMapper.getGroupMembersByGroupID(groupId);
    }

    @Override
    @Transactional
    public boolean inviteUsersToGroup(String groupId, String userIdsToInvite, String inviterId) {
        if(!userMapper.existsByUserid(userIdsToInvite)
                ||!groupMapper.existsByGroupId(groupId)
                ||groupMapper.isUserMember(groupId,userIdsToInvite)
                ||groupJoinRequestMapper.existsByUserid(userIdsToInvite)){
            return false;
        }
        else {
            String inviterName=userMapper.getUsernameById(inviterId);
            String id="jrq_" + IdUtil.getSnowflakeNextIdStr();
            String message="来源于" + inviterName + "的邀请";
            if (groupMapper.isNoVerificationToJoin(groupId)){
                Integer status=1;
                String mid="mem_"+ IdUtil.getSnowflakeNextIdStr();
                GroupJoinRequest groupJoinRequest=GroupJoinRequest.builder()
                        .id(id)
                        .groupId(groupId)
                        .userId(userIdsToInvite)
                        .inviterId(inviterId)
                        .message(message)
                        .status(status)
                        .build();
                groupMemberMapper.insertById(mid,groupId,userIdsToInvite);
                groupMapper.updateMemberCount(groupId,1);
                groupJoinRequestMapper.insert(groupJoinRequest);
            }
            else{
                GroupJoinRequest groupJoinRequest=GroupJoinRequest.builder()
                        .id(id)
                        .groupId(groupId)
                        .userId(userIdsToInvite)
                        .inviterId(inviterId)
                        .message(message)
                        .build();
                groupJoinRequestMapper.insert(groupJoinRequest);
            }
            return true;
        }
    }

    @Override
    @Transactional
    public String requestToJoinGroup(String groupId, String applicantId, String message) {
        try {
            // 检查用户是否存在
            if (!userMapper.existsByUserid(applicantId)) {
                throw new IllegalArgumentException("用户不存在");
            }

            // 检查群组是否存在
            if (!groupMapper.existsByGroupId(groupId)) {
                throw new IllegalArgumentException("群组不存在");
            }

            // 检查用户是否已经是群成员
            if (groupMapper.isUserMember(groupId, applicantId)) {
                throw new IllegalArgumentException("用户已是群成员");
            }

            // 检查是否已有待处理的加入请求
            if (groupJoinRequestMapper.existsByUserid(applicantId)) {
                throw new IllegalArgumentException("已有待处理的加入请求");
            }

            // 检查群组是否允许申请加入
            if (!groupMapper.isAllowToJoin(groupId)) {
                throw new ForbiddenException("该群只允许邀请加入");
            }

            // 所有检查通过，处理加入请求
            String id="jrq_" + IdUtil.getSnowflakeNextIdStr();
            if (groupMapper.isNoVerificationToJoin(groupId)){
                Integer status=1;
                String mid="mem_"+ IdUtil.getSnowflakeNextIdStr();
                GroupJoinRequest groupJoinRequest=GroupJoinRequest.builder()
                        .id(id)
                        .groupId(groupId)
                        .userId(applicantId)
                        .message(message)
                        .status(status)
                        .build();
                groupMemberMapper.insertById(mid,groupId,applicantId);
                groupMapper.updateMemberCount(groupId,1);
                groupJoinRequestMapper.insert(groupJoinRequest);
                return "申请已通过";
            }
            else{
                GroupJoinRequest groupJoinRequest=GroupJoinRequest.builder()
                        .id(id)
                        .groupId(groupId)
                        .userId(applicantId)
                        .message(message)
                        .build();
                groupJoinRequestMapper.insert(groupJoinRequest);
                return "申请已提交";
            }

        } catch (ForbiddenException | IllegalArgumentException e) {
            throw e; // 重新抛出给上层处理
        } catch (Exception e) {
            throw new RuntimeException("申请加入群组失败", e);
        }
    }

    @Override
    @Transactional
    public boolean quitGroup(String groupId, String userId) {
        try {
            // 检查用户是否是群主
            if (groupMapper.isGroupOwner(groupId, userId)) {
                throw new IllegalStateException("群主不能直接退出群组，请先转让群主身份");
            }

            // 检查用户是否是群成员
            if (!groupMapper.isUserMember(groupId, userId)) {
                throw new IllegalArgumentException("用户不是群成员");
            }

            // 执行退出群组操作
            int affectedRows = groupMemberMapper.delete(userId);
            groupMapper.updateMemberCount(groupId,-1);

            if (affectedRows == 0) {
                throw new RuntimeException("退出群组失败，可能数据已变更");
            }
            return true;
        } catch (DataAccessException e) {
            throw new RuntimeException("数据库操作失败", e);
        }
    }

    @Override
    public List<GroupResponse> searchGroups(String keyword) {
        return groupBriefResponseMapper.searchGroups(keyword);
    }

    @Override
    @Transactional
    public List<GroupJoinRequestResponse> getGroupJoinRequests(String groupId,String operatorId) {
        try {
            if(groupMapper.isAdmin(groupId,operatorId)){
                return groupJoinRequestResponseMapper.getGroupJoinRequests(groupId);
            }
            else{
                throw new ForbiddenException("当前用户无权查看群组加入请求");
            }
        } catch (DataAccessException e) {
            throw new RuntimeException("数据库访问失败，请稍后再试", e);
        }
    }

    @Override
    @Transactional
    public boolean handleJoinRequest(String groupId, String requestId, String action, String operatorId) {
        try {
            if(groupMapper.isAdmin(groupId,operatorId)&&groupJoinRequestMapper.isPendingByRequestId(requestId)){
                if(action.equals("accept")){
                    String mid="mem_"+ IdUtil.getSnowflakeNextIdStr();
                    String userid=groupJoinRequestMapper.getUseridById(requestId);
                    groupMemberMapper.insertById(mid,groupId,userid);
                    groupMapper.updateMemberCount(groupId,1);
                    groupJoinRequestMapper.updateStatus(requestId,1);
                    return true;
                }
                else if(action.equals("reject")){
                    groupJoinRequestMapper.updateStatus(requestId,2);
                    return true;
                }
                else
                    return false;
            }
            else{
                throw new ForbiddenException("请求被拒绝");
            }
        } catch (DataAccessException e) {
            throw new RuntimeException("数据库访问失败，请稍后再试", e);
        }
    }

    @Override
    public boolean canUserInvite(String groupId, String inviterId) {
        if(groupMapper.isGroupOwner(groupId,inviterId) || groupSettingMapper.isAllowMemberInvite(groupId))
            return true;
        else
            return false;
    }

    @Override
    public boolean isUserMember(String groupId, String userid) {
        return groupMapper.isUserMember(groupId,userid);
    }
}
