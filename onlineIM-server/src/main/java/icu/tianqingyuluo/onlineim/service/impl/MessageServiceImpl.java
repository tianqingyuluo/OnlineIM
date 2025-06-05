package icu.tianqingyuluo.onlineim.service.impl;

import cn.hutool.core.util.IdUtil;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.document.RecallLog;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.GroupMember;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.repository.RecallLogRepository;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.service.UserService;
import icu.tianqingyuluo.onlineim.util.LocalChannelRegistry;
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
    private final RecallLogRepository recallLogRepository;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final GroupMemberService groupMemberService;
    private final UserService userService;

    @Autowired
    public MessageServiceImpl(PrivateMessageRepository privateMessageRepository,
                              GroupMessageRepository groupMessageRepository,
                              RecallLogRepository recallLogRepository, GroupMemberService groupMemberService, UserService userService) {
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.recallLogRepository = recallLogRepository;
        this.groupMemberService = groupMemberService;
        this.userService = userService;
    }

    @Override
    public List<MessageResponse> getHistory(String conversationId, String seqId, Integer size, String userId) {
        // 根据会话ID的前缀判断是群聊还是单聊
        if (conversationId.startsWith("grp_")) {
            // 群聊消息
            return getGroupHistory(conversationId, seqId, size, userId);
        } else {
            // 单聊消息
            return getPrivateHistory(conversationId, seqId, size, userId);
        }
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
            
            // 设置消息状态为已撤回(3)
            privateMessage.setStatus(3);
            privateMessage.setUpdatedAt(new Date());
            privateMessageRepository.save(privateMessage);
            
            // 创建撤回日志记录
            RecallLog recallLog = RecallLog.builder()
                    .conversationId(privateMessage.getConversationId())
                    .seqId(privateMessage.getSeqId())
                    .messageId(messageId)
                    .operatorId(userId)
                    .recallTime(new Date())
                    .build();
            recallLogRepository.save(recallLog);
            
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
            
            // 设置消息状态为已撤回(3)
            groupMessage.setStatus(3);
            groupMessage.setUpdatedAt(new Date());
            groupMessageRepository.save(groupMessage);
            
            // 创建撤回日志记录
            RecallLog recallLog = RecallLog.builder()
                    .conversationId(groupMessage.getGroupId())
                    .seqId(groupMessage.getSeqId())
                    .messageId(messageId)
                    .operatorId(userId)
                    .recallTime(new Date())
                    .build();
            recallLogRepository.save(recallLog);
            
            // TODO: 使用redisEventPublisher向redis stream推送一个redisStreamEvent
            // event中包装拟定包装了消息撤回通知
            
            return true;
        }
        
        return false;
    }

    // 已删除标记消息已读功能

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
    public List<MessageResponse> syncMessages(String conversationId, String seqId, String userId) {
        // 根据会话ID的前缀判断是群聊还是单聊
        List<MessageResponse> messages = new ArrayList<>();
        long seqIdLong;
        try {
            seqIdLong = Long.parseLong(seqId);
        } catch (NumberFormatException e) {
            // 如果 seqId 不是有效的数字，返回空列表
            return messages;
        }
        
        if (conversationId.startsWith("grp_")) {
            // 群聊消息
            // 查询序列号大于 seqIdLong 的群聊消息
            List<GroupMessage> groupMessages = groupMessageRepository.findByGroupIdAndSeqIdGreaterThanOrderBySeqIdAsc(
                    conversationId, seqIdLong);
            
            // 转换为响应对象
            messages = groupMessages.stream()
                    .map(this::convertGroupMessageToResponse)
                    .collect(Collectors.toList());
        } else {
            // 单聊消息
            // 查询序列号大于 seqIdLong 的单聊消息
            List<PrivateMessage> privateMessages = privateMessageRepository.findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(
                    conversationId, seqIdLong);
            
            // 转换为响应对象
            messages = privateMessages.stream()
                    .map(this::convertPrivateMessageToResponse)
                    .collect(Collectors.toList());
        }
        
        return messages;
    }

    @Override
    public MessageResponse sendMessage(MessageSendRequest request, String userId) {
        // 根据targetId的前缀判断是私聊还是群聊
        String targetId = request.getTargetId();
        
        if (targetId.startsWith("usr_")) {
            // 私聊消息
            PrivateMessage message = new PrivateMessage();
            message.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
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
            message.setSeqId(IdUtil.getSnowflakeNextIdStr()); // 使用雪花ID作为序列号
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
            message.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
            message.setGroupId(targetId);
            message.setSenderId(userId);
            message.setMessageType(request.getMessageType());
            message.setContent(request.getContent());
            message.setStatus(0); // 发送中
            message.setSeqId(IdUtil.getSnowflakeNextIdStr()); // 使用雪花ID作为消息序列号
            
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
        String status = message.getStatus() == 0 ? "1" : String.valueOf(message.getStatus());
//        switch (message.getStatus()) {
//            case 0: status = "0"; break; // sending
//            case 1: status = "1"; break; // delivered
//            case 2: status = "2"; break; // failed
//            case 3: status = "3"; break; // recalled
//            default: status = "unknown";
//        }
        
        // 构建消息响应对象
        return MessageResponse.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .senderInfo(senderInfo)
                .messageType(message.getMessageType())
                .content(message.getContent())
                .status(status)
                .seqId(message.getSeqId())
                .clientMessageId(message.getClientMessageId())
                .isRecalled(message.getStatus() == 3)
                .timestamp(dateFormat.format(message.getTimestamp()))
                .build();
    }

    @Override
    public MessageResponse convertGroupMessageToResponse(GroupMessage message) {
        // 创建发送者信息
        UserBriefResponse senderInfo = userService.getUserBriefInfoByID(message.getSenderId());
        
        // 消息状态映射
        String status = message.getStatus() == 0 ? "1" : String.valueOf(message.getStatus());
//        switch (message.getStatus()) {
//            case 0: status = "sending"; break;
//            case 1: status = "delivered"; break;
//            case 2: status = "failed"; break;
//            case 3: status = "recalled"; break;
//            default: status = "unknown";
//        }
        
        // 构建消息响应对象
        return MessageResponse.builder()
                .messageId(message.getId())
                .conversationId(message.getGroupId()) // 群聊中使用groupId作为conversationId
                .senderInfo(senderInfo)
                .messageType(message.getMessageType())
                .content(message.getContent())
                .mentionedUserIds(message.getAtUsers())
                .status(status)
                .seqId(message.getSeqId())
                .clientMessageId(message.getClientMessageId())
                .isRecalled(message.getStatus() == 3)
                .timestamp(dateFormat.format(message.getTimestamp()))
                .build();
    }
    
    @Override
    public List<String> getRecallList(String conversationId, String seqId) {
        // 查询指定会话中序列号大于客户端当前序列号的所有撤回记录
        List<RecallLog> recallLogs = recallLogRepository.findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(
                conversationId, seqId);
        
        // 提取被撤回消息的序列号列表
        return recallLogs.stream()
                .map(RecallLog::getSeqId)
                .collect(Collectors.toList());
    }

    @Override
    public void saveGroupMessage(GroupMessage groupMessage) {
        if (groupMessageRepository.findByIdAndGroupId(groupMessage.getId(), groupMessage.getGroupId()) == null) {
            groupMessageRepository.save(groupMessage);
        }
    }

    @Override
    public void savePrivateMessage(PrivateMessage privateMessage) {
        if (privateMessageRepository.findByIdAndConversationId(privateMessage.getId(), privateMessage.getConversationId()) == null) {
            privateMessageRepository.save(privateMessage);
        }
    }

    @Override
    public void saveRecallLog(RecallLog recallLog) {
        recallLogRepository.save(recallLog);
    }

//    @Override
//    public List<String> getOnlineGroupMembers(String conversationId) {
//        List<GroupMemberResponse> members = groupMemberService.getGroupMembers(conversationId);
//        members.stream().forEach(  member -> {
//        });
//    }
}
