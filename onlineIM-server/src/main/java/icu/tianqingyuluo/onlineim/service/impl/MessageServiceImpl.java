package icu.tianqingyuluo.onlineim.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.exception.MessageContextUnavailableException;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.MessageReplySnapshot;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.document.RecallLog;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageContextResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReplyReferenceResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.repository.RecallLogRepository;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.MessageReplyService;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.service.UserService;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import icu.tianqingyuluo.onlineim.util.SeqIdComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息服务实现类
 */
@Service
public class MessageServiceImpl implements MessageService {
    private static final DateTimeFormatter MESSAGE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrivateMessageRepository privateMessageRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final RecallLogRepository recallLogRepository;
    private final GroupMemberService groupMemberService;
    private final UserService userService;
    private final MessageReplyService messageReplyService;
    private final ConversationRepository conversationRepository;
    private final RedisStreamService redisStreamService;
    private final ObjectMapper objectMapper;

    @Autowired
    public MessageServiceImpl(PrivateMessageRepository privateMessageRepository,
                              GroupMessageRepository groupMessageRepository,
                              RecallLogRepository recallLogRepository, GroupMemberService groupMemberService,
                              UserService userService, MessageReplyService messageReplyService,
                              ConversationRepository conversationRepository,
                              RedisStreamService redisStreamService,
                              ObjectMapper objectMapper) {
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.recallLogRepository = recallLogRepository;
        this.groupMemberService = groupMemberService;
        this.userService = userService;
        this.messageReplyService = messageReplyService;
        this.conversationRepository = conversationRepository;
        this.redisStreamService = redisStreamService;
        this.objectMapper = objectMapper;
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
            PrivateMessage refMessage = privateMessageRepository.findByConversationIdAndSeqId(conversationId, seqId);
            if (refMessage == null) {
                refMessage = privateMessageRepository.findById(seqId).orElse(null);
            }
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
        return convertPrivateMessagesToResponses(messages);
    }

    @Override
    public List<MessageResponse> getGroupHistory(String groupId, String seqId, Integer size, String userId) {
        List<GroupMessage> messages;
        Pageable pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        
        if (seqId != null && !seqId.isEmpty()) {
            // 查找指定序列号之前的消息
            GroupMessage refMessage = groupMessageRepository.findByGroupIdAndSeqId(groupId, seqId);
            if (refMessage == null) {
                refMessage = groupMessageRepository.findById(seqId).orElse(null);
            }
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
        return convertGroupMessagesToResponses(messages);
    }

    @Override
    public MessageContextResponse getContext(String conversationId, String messageId,
                                             Integer before, Integer after, String userId) {
        int beforeLimit = clampWindow(before);
        int afterLimit = clampWindow(after);
        assertContextAccess(conversationId, userId);

        List<MessageResponse> responses = new ArrayList<>();
        boolean hasMoreBefore;
        boolean hasMoreAfter;
        if (conversationId.startsWith("grp_")) {
            GroupMessage target = groupMessageRepository.findByIdAndGroupId(messageId, conversationId);
            if (target == null) {
                throw new MessageContextUnavailableException();
            }
            String targetSeqId = requireContextSeqId(target.getSeqId());
            List<GroupMessage> beforeMessages = groupMessageRepository.findMessagesBeforeSeqId(
                    conversationId, targetSeqId, contextPage(beforeLimit));
            List<GroupMessage> afterMessages = groupMessageRepository.findMessagesAfterSeqId(
                    conversationId, targetSeqId, contextPage(afterLimit));
            hasMoreBefore = beforeMessages.size() > beforeLimit;
            hasMoreAfter = afterMessages.size() > afterLimit;
            List<GroupMessage> window = new ArrayList<>();
            beforeMessages.stream().limit(beforeLimit).forEach(window::add);
            window.add(target);
            afterMessages.stream().limit(afterLimit).forEach(window::add);
            responses.addAll(convertGroupMessagesToResponses(window));
        } else {
            PrivateMessage target = privateMessageRepository.findByIdAndConversationId(messageId, conversationId);
            if (target == null) {
                throw new MessageContextUnavailableException();
            }
            String targetSeqId = requireContextSeqId(target.getSeqId());
            List<PrivateMessage> beforeMessages = privateMessageRepository.findMessagesBeforeSeqId(
                    conversationId, targetSeqId, contextPage(beforeLimit));
            List<PrivateMessage> afterMessages = privateMessageRepository.findMessagesAfterSeqId(
                    conversationId, targetSeqId, contextPage(afterLimit));
            hasMoreBefore = beforeMessages.size() > beforeLimit;
            hasMoreAfter = afterMessages.size() > afterLimit;
            List<PrivateMessage> window = new ArrayList<>();
            beforeMessages.stream().limit(beforeLimit).forEach(window::add);
            window.add(target);
            afterMessages.stream().limit(afterLimit).forEach(window::add);
            responses.addAll(convertPrivateMessagesToResponses(window));
        }
        responses.sort((left, right) -> SeqIdComparator.compare(left.getSeqId(), right.getSeqId()));
        return MessageContextResponse.builder()
                .targetMessageId(messageId)
                .messages(responses)
                .hasMoreBefore(hasMoreBefore)
                .hasMoreAfter(hasMoreAfter)
                .build();
    }

    private Pageable contextPage(int requested) {
        return PageRequest.of(0, requested + 1);
    }

    private String requireContextSeqId(String seqId) {
        try {
            return SeqIdComparator.requireValid(seqId);
        } catch (IllegalArgumentException e) {
            throw new MessageContextUnavailableException();
        }
    }

    private int clampWindow(Integer value) {
        if (value == null) return 20;
        return Math.max(0, Math.min(50, value));
    }

    private void assertContextAccess(String conversationId, String userId) {
        boolean allowed = conversationId != null && userId != null && (conversationId.startsWith("grp_")
                ? groupMemberService.isGroupMember(conversationId, userId)
                : conversationRepository.findByIdAndUserIDOrTargetId(conversationId, userId) != null);
        if (!allowed) {
            throw new MessageContextUnavailableException();
        }
    }

    private MessageReplySnapshot createReplySnapshot(String conversationId, String messageId, String userId) {
        return messageId == null || messageId.isBlank()
                ? null
                : messageReplyService.createSnapshot(conversationId, messageId, userId);
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
            messageReplyService.markTargetRecalled(privateMessage.getConversationId(), messageId);
            
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
            messageReplyService.markTargetRecalled(groupMessage.getGroupId(), messageId);
            
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
        String normalizedSeqId;
        try {
            normalizedSeqId = SeqIdComparator.requireValid(seqId);
        } catch (IllegalArgumentException e) {
            // 如果 seqId 不是有效的数字，返回空列表
            return messages;
        }
        
        if (conversationId.startsWith("grp_")) {
            // 群聊消息
            // 查询序列号大于 normalizedSeqId 的群聊消息
            List<GroupMessage> groupMessages = groupMessageRepository.findByGroupIdAndSeqIdGreaterThanOrderBySeqIdAsc(
                    conversationId, normalizedSeqId);
            
            // 转换为响应对象
            List<GroupMessage> sortedMessages = groupMessages.stream()
                    .sorted((left, right) -> SeqIdComparator.compare(left.getSeqId(), right.getSeqId()))
                    .toList();
            messages = convertGroupMessagesToResponses(sortedMessages);
        } else {
            // 单聊消息
            // 查询序列号大于 normalizedSeqId 的单聊消息
            List<PrivateMessage> privateMessages = privateMessageRepository.findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(
                    conversationId, normalizedSeqId);
            
            // 转换为响应对象
            List<PrivateMessage> sortedMessages = privateMessages.stream()
                    .sorted((left, right) -> SeqIdComparator.compare(left.getSeqId(), right.getSeqId()))
                    .toList();
            messages = convertPrivateMessagesToResponses(sortedMessages);
        }
        
        return messages;
    }

    @Override
    public MessageResponse sendMessage(MessageSendRequest request, String userId) {
        validateSendRequest(request, userId);
        String targetId = request.getTargetId();

        if (targetId.startsWith("usr_")) {
            if (userId.equals(targetId)) {
                throw new IllegalArgumentException("不能给自己发送私聊消息");
            }
            String conversationId = ConversationIdUtil.privateConversationId(userId, targetId);
            assertPrivateConversationAccess(conversationId, userId, targetId);

            PrivateMessage existing = privateMessageRepository.findBySenderIdAndClientMessageId(
                    userId, request.getClientMsgId());
            if (existing != null) {
                return convertPrivateMessageToResponse(existing);
            }

            PrivateMessage message = new PrivateMessage();
            message.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
            message.setSenderId(userId);
            message.setReceiverId(targetId);
            message.setConversationId(conversationId);
            message.setMessageType(request.getMessageType());
            message.setContent(request.getContent());
            message.setReplyTo(createReplySnapshot(conversationId, request.getReplyToMessageId(), userId));
            message.setContentRevision(1);
            message.setClientMessageId(request.getClientMsgId());
            message.setStatus(0);
            message.setSeqId(IdUtil.getSnowflakeNextIdStr());
            message.setTimestamp(new Date());
            message.setCreatedAt(new Date());
            message.setUpdatedAt(new Date());
            message.setExt(request.getExt());

            privateMessageRepository.save(message);
            redisStreamService.publishPrivateMessage(
                    "PRIVATE_MESSAGE",
                    userId,
                    serializeMessage(message),
                    targetId);

            return convertPrivateMessageToResponse(message);
        } else if (targetId.startsWith("grp_")) {
            if (!groupMemberService.isGroupMember(targetId, userId)) {
                throw new ForbiddenException("无权在该群聊发送消息");
            }

            GroupMessage existing = groupMessageRepository.findBySenderIdAndClientMessageId(
                    userId, request.getClientMsgId());
            if (existing != null) {
                return convertGroupMessageToResponse(existing);
            }

            GroupMessage message = new GroupMessage();
            message.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
            message.setGroupId(targetId);
            message.setSenderId(userId);
            message.setMessageType(request.getMessageType());
            message.setContent(request.getContent());
            message.setReplyTo(createReplySnapshot(targetId, request.getReplyToMessageId(), userId));
            message.setContentRevision(1);
            message.setClientMessageId(request.getClientMsgId());
            message.setStatus(0);
            message.setSeqId(IdUtil.getSnowflakeNextIdStr());

            if (request.getAtUserIds() != null && !request.getAtUserIds().isEmpty()) {
                message.setAtUsers(request.getAtUserIds());
            }

            message.setTimestamp(new Date());
            message.setCreatedAt(new Date());
            message.setUpdatedAt(new Date());
            message.setExt(request.getExt());

            groupMessageRepository.save(message);
            redisStreamService.publishGroupMessage(
                    "GROUP_MESSAGE",
                    userId,
                    serializeMessage(message),
                    groupReceiverIds(targetId));

            return convertGroupMessageToResponse(message);
        }

        throw new IllegalArgumentException("不支持的接收方ID");
    }

    private void validateSendRequest(MessageSendRequest request, String userId) {
        if (request == null) {
            throw new IllegalArgumentException("消息请求不能为空");
        }
        if (userId == null || userId.isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        if (request.getTargetId() == null || request.getTargetId().isBlank()) {
            throw new IllegalArgumentException("接收方ID不能为空");
        }
        if (request.getMessageType() == null || request.getMessageType().isBlank()) {
            throw new IllegalArgumentException("消息类型不能为空");
        }
        if (request.getContent() == null) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (request.getClientMsgId() == null || request.getClientMsgId().isBlank()) {
            throw new IllegalArgumentException("客户端消息ID不能为空");
        }
    }

    private void assertPrivateConversationAccess(String conversationId, String senderId, String receiverId) {
        Conversation conversation = conversationRepository.findByIdAndUserIDOrTargetId(conversationId, senderId);
        boolean participantsMatch = conversation != null
                && "private".equals(conversation.getConversationType())
                && ((senderId.equals(conversation.getUserId()) && receiverId.equals(conversation.getTargetId()))
                || (senderId.equals(conversation.getTargetId()) && receiverId.equals(conversation.getUserId())));
        if (!participantsMatch) {
            throw new ForbiddenException("无权访问该私聊会话");
        }
    }

    private List<String> groupReceiverIds(String groupId) {
        List<GroupMemberResponse> members = groupMemberService.getGroupMembers(groupId);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .map(GroupMemberResponse::getUserInfo)
                .filter(Objects::nonNull)
                .map(UserBriefResponse::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String serializeMessage(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("消息序列化失败", e);
        }
    }

    @Override
    public MessageResponse convertPrivateMessageToResponse(PrivateMessage message) {
        return buildPrivateMessageResponse(
                message,
                messageReplyService.resolveForResponse(message.getConversationId(), message.getReplyTo()));
    }

    private MessageResponse buildPrivateMessageResponse(
            PrivateMessage message, ReplyReferenceResponse replyReference) {
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
                .replyTo(replyReference)
                .status(status)
                .deliveryState("sent")
                .seqId(message.getSeqId())
                .clientMessageId(message.getClientMessageId())
                .isRecalled(message.getStatus() == 3)
                .timestamp(formatTimestamp(message.getTimestamp()))
                .build();
    }

    @Override
    public MessageResponse convertGroupMessageToResponse(GroupMessage message) {
        return buildGroupMessageResponse(
                message,
                messageReplyService.resolveForResponse(message.getGroupId(), message.getReplyTo()));
    }

    private MessageResponse buildGroupMessageResponse(
            GroupMessage message, ReplyReferenceResponse replyReference) {
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
                .replyTo(replyReference)
                .mentionedUserIds(message.getAtUsers())
                .status(status)
                .deliveryState("sent")
                .seqId(message.getSeqId())
                .clientMessageId(message.getClientMessageId())
                .isRecalled(message.getStatus() == 3)
                .timestamp(formatTimestamp(message.getTimestamp()))
                .build();
    }

    private List<MessageResponse> convertPrivateMessagesToResponses(List<PrivateMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ReplyReferenceResponse> replies = resolveBatchReplies(
                messages.getFirst().getConversationId(),
                messages.stream().map(PrivateMessage::getReplyTo).toList());
        List<MessageResponse> responses = new ArrayList<>(messages.size());
        for (int index = 0; index < messages.size(); index++) {
            responses.add(buildPrivateMessageResponse(messages.get(index), replies.get(index)));
        }
        return responses;
    }

    private List<MessageResponse> convertGroupMessagesToResponses(List<GroupMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ReplyReferenceResponse> replies = resolveBatchReplies(
                messages.getFirst().getGroupId(),
                messages.stream().map(GroupMessage::getReplyTo).toList());
        List<MessageResponse> responses = new ArrayList<>(messages.size());
        for (int index = 0; index < messages.size(); index++) {
            responses.add(buildGroupMessageResponse(messages.get(index), replies.get(index)));
        }
        return responses;
    }

    private List<ReplyReferenceResponse> resolveBatchReplies(
            String conversationId, List<MessageReplySnapshot> snapshots) {
        if (snapshots.stream().noneMatch(Objects::nonNull)) {
            return new ArrayList<>(Collections.nCopies(snapshots.size(), null));
        }
        return messageReplyService.resolveBatchForResponses(conversationId, snapshots);
    }

    private String formatTimestamp(Date timestamp) {
        if (timestamp == null) {
            return null;
        }
        return MESSAGE_TIMESTAMP_FORMATTER.format(
                timestamp.toInstant().atZone(ZoneId.systemDefault()));
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
