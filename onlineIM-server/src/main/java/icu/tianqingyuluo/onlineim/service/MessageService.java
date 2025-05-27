package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 消息服务接口
 */
public interface MessageService {

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

    /**
     * 撤回消息
     * @param messageId 消息ID
     * @param userId 当前用户ID
     * @return 是否成功
     */
    boolean recallMessage(String messageId, String userId);

    /**
     * 标记消息已读
     * @param messageIds 消息ID列表
     * @param userId 当前用户ID
     * @return 是否成功
     */
    boolean markAsRead(List<String> messageIds, String userId);

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
     * @param seqId 客户端当前序列号
     * @param userId 当前用户ID
     * @return 增量消息列表
     */
    List<MessageResponse> syncMessages(String seqId, String userId);

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
}
