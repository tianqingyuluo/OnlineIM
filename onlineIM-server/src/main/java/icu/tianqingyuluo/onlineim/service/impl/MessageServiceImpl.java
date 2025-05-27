package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息服务实现类
 */
@Service
public class MessageServiceImpl implements MessageService {

    private final PrivateMessageRepository privateMessageRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public MessageServiceImpl(PrivateMessageRepository privateMessageRepository,
                             GroupMessageRepository groupMessageRepository) {
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
    }

    @Override
    public List<MessageResponse> getPrivateHistory(String conversationId, String seqId, Integer size, String userId) {
        List<PrivateMessage> messages;
        Pageable pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        
        if (seqId != null && !seqId.isEmpty()) {
            // 查找指定序列号之前的消息
            PrivateMessage refMessage = privateMessageRepository.findById(seqId).orElse(null);
            if (refMessage != null) {
                messages = privateMessageRepository.findMessagesBeforeTimestamp(
                        conversationId, refMessage.getTimestamp(), pageable);
            } else {
                // 如果找不到参考消息，则返回最新的消息
                messages = privateMessageRepository.findByConversationIdOrderByTimestampDesc(
                        conversationId, pageable).getContent();
            }
        } else {
            // 不指定序列号，返回最新的消息
            messages = privateMessageRepository.findByConversationIdOrderByTimestampDesc(
                    conversationId, pageable).getContent();
        }
        
        // 转换为响应对象
        return messages.stream()
                .map(this::convertPrivateMessageToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageResponse> getGroupHistory(String groupId, String seqId, Integer size, String userId) {
        List<GroupMessage> messages;
        Pageable pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        
        if (seqId != null && !seqId.isEmpty()) {
            // 查找指定序列号之前的消息
            GroupMessage refMessage = groupMessageRepository.findById(seqId).orElse(null);
            if (refMessage != null) {
                messages = groupMessageRepository.findMessagesBeforeTimestamp(
                        groupId, refMessage.getTimestamp(), pageable);
            } else {
                // 如果找不到参考消息，则返回最新的消息
                messages = groupMessageRepository.findByGroupIdOrderByTimestampDesc(
                        groupId, pageable).getContent();
            }
        } else {
            // 不指定序列号，返回最新的消息
            messages = groupMessageRepository.findByGroupIdOrderByTimestampDesc(
                    groupId, pageable).getContent();
        }
        
        // 转换为响应对象
        return messages.stream()
                .map(this::convertGroupMessageToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public boolean recallMessage(String messageId, String userId) {
        // 检查是否为私聊消息
        PrivateMessage privateMessage = privateMessageRepository.findById(messageId).orElse(null);
        if (privateMessage != null) {
            // 只有发送者才能撤回消息
            if (!privateMessage.getSenderId().equals(userId)) {
                return false;
            }
            
            // 设置消息状态为已撤回(4)
            privateMessage.setStatus(4);
            privateMessage.setUpdatedAt(new Date());
            privateMessageRepository.save(privateMessage);
            
            // TODO: 使用redisEventPublisher向redis stream推送一个redisStreamEvent
            // event中包装拟定包装了消息撤回通知
            
            return true;
        }
        
        // 检查是否为群聊消息
        GroupMessage groupMessage = groupMessageRepository.findById(messageId).orElse(null);
        if (groupMessage != null) {
            // 只有发送者才能撤回消息
            if (!groupMessage.getSenderId().equals(userId)) {
                return false;
            }
            
            // 设置消息状态为已撤回(4)
            groupMessage.setStatus(4);
            groupMessage.setUpdatedAt(new Date());
            groupMessageRepository.save(groupMessage);
            
            // TODO: 使用redisEventPublisher向redis stream推送一个redisStreamEvent
            // event中包装拟定包装了消息撤回通知
            
            return true;
        }
        
        return false;
    }

    @Override
    public boolean markAsRead(List<String> messageIds, String userId) {
        // 处理私聊消息
        List<PrivateMessage> privateMessages = privateMessageRepository.findAllById(messageIds);
        for (PrivateMessage message : privateMessages) {
            // 只有接收者才能标记消息为已读
            if (message.getReceiverId().equals(userId)) {
                message.setStatus(2); // 已读状态
                message.setUpdatedAt(new Date());
                privateMessageRepository.save(message);
                
                // TODO: 可以考虑使用WebSocket通知发送者消息已读
            }
        }
        
        // 注意：群聊消息的已读状态处理可能需要更复杂的逻辑，这里简化处理
        
        return true;
    }

    @Override
    public Map<String, String> uploadFile(String userId, String type, MultipartFile file) {
        // 验证文件类型
        if (!Arrays.asList("image", "voice", "video", "file").contains(type)) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "不支持的文件类型");
            return error;
        }
        
        // TODO: 使用FileStorageService调用对象存储
        // 这里模拟上传成功，返回一个假的URL
        String fileUrl = "https://example.com/files/" + UUID.randomUUID().toString() + 
                         "_" + file.getOriginalFilename();
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("url", fileUrl);
        result.put("type", type);
        result.put("name", file.getOriginalFilename());
        result.put("size", String.valueOf(file.getSize()));
        
        return result;
    }

    @Override
    public List<MessageResponse> syncMessages(String seqId, String userId) {
        // 这里需要根据用户ID和序列号查询新消息
        // 实际实现可能需要更复杂的逻辑，这里简化处理
        
        // TODO: 实现增量同步消息逻辑
        // 1. 查询用户的所有会话
        // 2. 对每个会话，查询序列号大于seqId的消息
        // 3. 合并所有消息并按时间排序
        
        // 这里返回一个空列表作为示例
        return new ArrayList<>();
    }

    @Override
    public MessageResponse sendMessage(MessageSendRequest request, String userId) {
        // 根据targetId的前缀判断是私聊还是群聊
        String targetId = request.getTargetId();
        
        if (targetId.startsWith("usr_")) {
            // 私聊消息
            PrivateMessage message = new PrivateMessage();
            message.setId("msg_" + UUID.randomUUID().toString());
            message.setSenderId(userId);
            message.setReceiverId(targetId);
            
            // 生成会话ID (确保两个用户之间的会话ID是唯一的且一致的)
            String[] ids = new String[]{userId, targetId};
            Arrays.sort(ids);
            String conversationId = "conv_" + ids[0] + "_" + ids[1];
            message.setConversationId(conversationId);
            
            message.setMessageType(request.getMessageType());
            message.setContent(request.getContent());
            message.setStatus(0); // 发送中
            message.setClientMsgId(request.getClientMsgId());
            message.setSeqId(System.currentTimeMillis()); // 使用时间戳作为序列号
            message.setTimestamp(new Date());
            message.setCreatedAt(new Date());
            message.setUpdatedAt(new Date());
            
            // 保存消息
            privateMessageRepository.save(message);
            
            // TODO: 使用WebSocket或其他方式通知接收者
            
            // 返回消息响应
            return convertPrivateMessageToResponse(message);
            
        } else if (targetId.startsWith("grp_")) {
            // 群聊消息
            GroupMessage message = new GroupMessage();
            message.setId("msg_" + UUID.randomUUID().toString());
            message.setGroupId(targetId);
            message.setSenderId(userId);
            message.setMessageType(request.getMessageType());
            message.setContent(request.getContent());
            message.setStatus(0); // 发送中
            message.setClientMsgId(request.getClientMsgId());
            message.setSeqId(System.currentTimeMillis()); // 使用时间戳作为序列号
            
            // 处理@用户
            if (request.getAtUserIds() != null && !request.getAtUserIds().isEmpty()) {
                message.setAtUsers(request.getAtUserIds());
            }
            
            message.setTimestamp(new Date());
            message.setCreatedAt(new Date());
            message.setUpdatedAt(new Date());
            
            // 保存消息
            groupMessageRepository.save(message);
            
            // TODO: 使用WebSocket或其他方式通知群成员
            
            // 返回消息响应
            return convertGroupMessageToResponse(message);
        }
        
        return null;
    }

    @Override
    public MessageResponse convertPrivateMessageToResponse(PrivateMessage message) {
        // 创建发送者信息
        UserBriefResponse senderInfo = UserBriefResponse.builder()
                .userId(message.getSenderId())
                // 这里需要根据senderId查询用户信息，补充name和avatar等字段
                .username("用户" + message.getSenderId())
                .avatarUrl("https://example.com/avatar.jpg")
                .build();
        
        // 消息状态映射
        String status;
        switch (message.getStatus()) {
            case 0: status = "sending"; break;
            case 1: status = "delivered"; break;
            case 2: status = "read"; break;
            case 3: status = "failed"; break;
            case 4: status = "recalled"; break;
            default: status = "unknown";
        }
        
        // 构建消息响应对象
        return MessageResponse.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .senderInfo(senderInfo)
                .messageType(message.getMessageType())
                .content(message.getContent())
                .status(status)
                .isRecalled(message.getStatus() == 4)
                .timestamp(dateFormat.format(message.getTimestamp()))
                .clientMessageId(message.getClientMsgId())
                .build();
    }

    @Override
    public MessageResponse convertGroupMessageToResponse(GroupMessage message) {
        // 创建发送者信息
        UserBriefResponse senderInfo = UserBriefResponse.builder()
                .userId(message.getSenderId())
                // 这里需要根据senderId查询用户信息，补充name和avatar等字段
                .username("用户" + message.getSenderId())
                .avatarUrl("https://example.com/avatar.jpg")
                .build();
        
        // 消息状态映射
        String status;
        switch (message.getStatus()) {
            case 0: status = "sending"; break;
            case 1: status = "delivered"; break;
            case 2: status = "read"; break;
            case 3: status = "failed"; break;
            case 4: status = "recalled"; break;
            default: status = "unknown";
        }
        
        // 构建消息响应对象
        return MessageResponse.builder()
                .messageId(message.getId())
                .conversationId(message.getGroupId()) // 群聊中使用groupId作为conversationId
                .senderInfo(senderInfo)
                .messageType(message.getMessageType())
                .content(message.getContent())
                .mentionedUserIds(message.getAtUsers())
                .status(status)
                .isRecalled(message.getStatus() == 4)
                .timestamp(dateFormat.format(message.getTimestamp()))
                .clientMessageId(message.getClientMsgId())
                .build();
    }
}
