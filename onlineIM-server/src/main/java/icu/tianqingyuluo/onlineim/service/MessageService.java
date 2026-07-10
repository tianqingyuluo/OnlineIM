package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.document.RecallLog;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageContextResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 消息服务接口
 */
public interface MessageService {

    /**
     * 获取会话历史消息，支持群聊和单聊
     * @param conversationId 会话ID（可以是群聊ID或单聊ID）
     * @param seqId 消息序列号（可选）
     * @param size 消息数量
     * @param userId 当前用户ID
     * @return 消息列表
     */
    List<MessageResponse> getHistory(String conversationId, String seqId, Integer size, String userId);
    
    /**
     * 获取私聊历史消息
     * @param conversationId 会话ID
     * @param seqId 消息序列号（可选）
     * @param size 消息数量
     * @param userId 当前用户ID
     * @return 消息列表
     */
    List<MessageResponse> getPrivateHistory(String conversationId, String seqId, Integer size, String userId);

    /**
     * 获取群聊历史消息
     * @param groupId 群组ID
     * @param seqId 消息序列号（可选）
     * @param size 消息数量
     * @param userId 当前用户ID
     * @return 消息列表
     */
    List<MessageResponse> getGroupHistory(String groupId, String seqId, Integer size, String userId);

    MessageContextResponse getContext(String conversationId, String messageId,
                                      Integer before, Integer after, String userId);

    /**
     * 撤回消息
     * @param messageId 消息ID
     * @param userId 当前用户ID
     * @return 是否成功
     */
    boolean recallMessage(String messageId, String userId);

    // 已删除标记消息已读功能

    /**
     * 上传文件
     * @param userId 用户ID
     * @param type 文件类型
     * @param file 文件内容
     * @return 上传结果
     */
    Map<String, String> uploadFile(String userId, String type, MultipartFile file);

    /**
     * 增量同步消息
     * @param conversationId 会话ID（可以是群聊ID或单聊ID）
     * @param seqId 客户端当前序列号
     * @param userId 当前用户ID
     * @return 增量消息列表
     */
    List<MessageResponse> syncMessages(String conversationId, String seqId, String userId);

    /**
     * 发送消息
     * @param request 消息请求
     * @param userId 发送者ID
     * @return 消息响应
     */
    MessageResponse sendMessage(MessageSendRequest request, String userId);

    /**
     * 将私聊消息转换为响应对象
     * @param message 私聊消息
     * @return 消息响应
     */
    MessageResponse convertPrivateMessageToResponse(PrivateMessage message);

    /**
     * 将群聊消息转换为响应对象
     * @param message 群聊消息
     * @return 消息响应
     */
    MessageResponse convertGroupMessageToResponse(GroupMessage message);
    
    /**
     * 获取指定会话中需要撤回的消息序列号列表
     *
     * @param conversationId 会话ID
     * @param seqId          客户端当前序列号
     * @return 需要撤回的消息序列号列表
     */
    List<String> getRecallList(String conversationId, String seqId);

    /**
     * 存储群聊消息
     * @param groupMessage 群聊消息
     */
    void saveGroupMessage(GroupMessage groupMessage);

    /**
     * 存储私聊消息
     * @param privateMessage 私聊消息
     */
    void savePrivateMessage(PrivateMessage privateMessage);

    /**
     * 存储撤回日志
     * @param recallLog 撤回日志
     */
    void saveRecallLog(RecallLog recallLog);

    /**
     * 获取当前在线的群聊成员的userId
     * @param conversationId 会话的Id（群聊id）
     * @return 当前在线的群聊成员的userId
     */
//    List<String> getOnlineGroupMembers(String conversationId);


}
