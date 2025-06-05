package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendGroupResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface FriendGroupService {
    /**
     * 获取用户的好友分组列表
     * @param userId 用户ID
     * @return 好友分组列表
     * @throws Exception 数据库操作异常
     */
    List<FriendGroupResponse> getFriendGroupsByUserId(String userId) throws Exception;

    /**
     * 创建好友分组
     * @param userId 用户ID
     * @param request 创建请求
     * @return 创建的分组信息
     * @throws Exception 数据库操作异常
     */
    FriendGroupResponse createFriendGroup(String userId, FriendGroupCreateRequest request) throws Exception;

    /**
     * 更新好友分组
     * @param userId 用户ID
     * @param groupId 分组ID
     * @param request 更新请求
     * @return 更新后的分组信息，如果分组不存在返回null
     * @throws Exception 数据库操作异常
     */
    FriendGroupResponse updateFriendGroup(String userId, String groupId, FriendGroupUpdateRequest request) throws Exception;

    /**
     * 删除好友分组
     * @param userId 用户ID
     * @param groupId 分组ID
     * @return 是否删除成功
     * @throws Exception 数据库操作异常
     */
    boolean deleteFriendGroup(String userId, String groupId) throws Exception;

    /**
     * 调整好友分组顺序
     * @param userId 用户ID
     * @param request 排序请求，包含分组ID和顺序信息
     * @return 是否排序成功
     * @throws Exception 数据库操作异常
     */
    boolean sortFriendGroups(String userId, List<Map<String, Object>> request) throws Exception;
}
