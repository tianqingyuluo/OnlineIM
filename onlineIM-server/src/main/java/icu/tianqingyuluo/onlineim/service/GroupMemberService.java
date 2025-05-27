package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface GroupMemberService {
    /**
     *  获取群组成员信息
     * @param groupId 群组ID
     */
    List<GroupMemberResponse> getGroupMembers(String groupId);

    /**
     * 获取群成员详情
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @param operatorId 操作者ID
     * @return 成员详情
     */
    GroupMemberResponse getMemberDetail(String groupId, String memberId, String operatorId);
    
    /**
     * 移除群成员
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @param operatorId 操作者ID
     * @return 是否成功
     */
    boolean removeMember(String groupId, String memberId, String operatorId);
    
    /**
     * 设置群管理员
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @param operatorId 操作者ID
     * @return 是否成功
     */
    boolean setAdmin(String groupId, String memberId, String operatorId);
    
    /**
     * 取消群管理员
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @param operatorId 操作者ID
     * @return 是否成功
     */
    boolean cancelAdmin(String groupId, String memberId, String operatorId);
    
    /**
     * 更新群成员昵称
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @param nickname 新昵称
     * @param operatorId 操作者ID
     * @return 是否成功
     */
    boolean updateMemberNickname(String groupId, String memberId, String nickname, String operatorId);
    
    /**
     * 禁言群成员
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @param duration 禁言时长（分钟）
     * @param operatorId 操作者ID
     * @return 是否成功
     */
    boolean muteMember(String groupId, String memberId, Integer duration, String operatorId);
    
    /**
     * 取消群成员禁言
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @param operatorId 操作者ID
     * @return 是否成功
     */
    boolean unmuteMember(String groupId, String memberId, String operatorId);
    
    /**
     * 转让群主
     * @param groupId 群组ID
     * @param memberId 成员ID（新群主）
     * @param operatorId 操作者ID（当前群主）
     * @return 是否成功
     */
    boolean transferOwnership(String groupId, String memberId, String operatorId);

    boolean isGroupMember(String groupId, String userIDFromToken);
}
