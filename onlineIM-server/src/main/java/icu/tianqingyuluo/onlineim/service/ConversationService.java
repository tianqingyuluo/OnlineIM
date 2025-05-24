package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ConversationResponse;

import java.util.List;
import java.util.Map;

/**
 * 会话服务接口
 */
public interface ConversationService {

    /**
     * 获取用户的会话列表
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ConversationResponse> getUserConversations(String userId);

    /**
     * 获取会话详情
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 会话详情
     */
    ConversationResponse getConversation(String conversationId, String userId);

    /**
     * 删除会话
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteConversation(String conversationId, String userId);

    /**
     * 置顶会话
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean topConversation(String conversationId, String userId);

    /**
     * 取消置顶会话
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean untopConversation(String conversationId, String userId);

    /**
     * 设置会话免打扰
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean muteConversation(String conversationId, String userId);

    /**
     * 取消会话免打扰
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean unmuteConversation(String conversationId, String userId);

    /**
     * 清空会话消息
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean clearMessages(String conversationId, String userId);

    /**
     * 获取用户未读消息数
     * @param userId 用户ID
     * @return 未读消息数信息
     */
    Map<String, Object> getUnreadCount(String userId);

    /**
     * 将数据库实体转换为响应DTO
     * @param conversation 会话实体
     * @return 会话响应DTO
     */
    ConversationResponse convertToConversationResponse(Conversation conversation);
}
