package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupSettingUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupSettingResponse;
import org.springframework.stereotype.Service;

@Service
public interface GroupSettingService {
    /**
     * 获取群组设置
     * @param groupId 群组ID
     * @param operatorId 操作者ID
     * @return 群组设置
     */
    GroupSettingResponse getGroupSettings(String groupId, String operatorId);
    
    /**
     * 更新群组设置
     * @param groupId 群组ID
     * @param request 更新请求
     * @param operatorId 操作者ID
     * @return 更新后的群组设置
     */
    GroupSettingResponse updateGroupSettings(String groupId, GroupSettingUpdateRequest request, String operatorId);
    
    /**
     * 启用全员禁言
     * @param groupId 群组ID
     * @param operatorId 操作者ID
     * @return 更新后的群组设置
     */
    GroupSettingResponse muteAll(String groupId, String operatorId);
    
    /**
     * 取消全员禁言
     * @param groupId 群组ID
     * @param operatorId 操作者ID
     * @return 更新后的群组设置
     */
    GroupSettingResponse unmuteAll(String groupId, String operatorId);
}
