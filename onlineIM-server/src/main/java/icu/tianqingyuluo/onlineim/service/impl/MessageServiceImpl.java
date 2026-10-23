package icu.tianqingyuluo.onlineim.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.exception.MessageContextUnavailableException;
import icu.tianqingyuluo.onlineim.exception.MessageRecallException;
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
import icu.tianqingyuluo.onlineim.service.ChatMediaService;
import icu.tianqingyuluo.onlineim.service.ConversationRecordService;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.MessageReplyService;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.service.UserService;
import icu.tianqingyuluo.onlineim.util.MentionUtil;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import icu.tianqingyuluo.onlineim.util.SeqIdComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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
    private final FriendService friendService;
    private final ConversationRecordService conversationRecords;
    private final ChatMediaService chatMediaService;
    private long recallWindowSeconds = 120;

    @Autowired
    public MessageServiceImpl(PrivateMessageRepository privateMessageRepository,
                              GroupMessageRepository groupMessageRepository,
                              RecallLogRepository recallLogRepository, GroupMemberService groupMemberService,
                              UserService userService, MessageReplyService messageReplyService,
                              ConversationRepository conversationRepository,
                              RedisStreamService redisStreamService,
                              ObjectMapper objectMapper,
                              FriendService friendService,
                              ConversationRecordService conversationRecords,
                              ChatMediaService chatMediaService) {
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.recallLogRepository = recallLogRepository;
        this.groupMemberService = groupMemberService;
        this.userService = userService;
        this.messageReplyService = messageReplyService;
        this.conversationRepository = conversationRepository;
        this.redisStreamService = redisStreamService;
        this.objectMapper = objectMapper;
        this.friendService = friendService;
        this.conversationRecords = conversationRecords;
        this.chatMediaService = chatMediaService;
    }

    @Override
    public List<MessageResponse> getHistory(String conversationId, String seqId, Integer size, String userId) {
        assertReadable(conversationId, userId);
        // 根据会话ID的前缀判断是群聊还是单聊
        if (conversationId.startsWith("grp_")) {
            return getGroupHistory(conversationId, seqId, size, userId);
        } else {
            return getPrivateHistory(conversationId, seqId, size, userId);
        }
    }
    
    @Override
    public List<MessageResponse> getPrivateHistory(String conversationId, String seqId, Integer size, String userId) {
        assertReadable(conversationId, userId);
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
        assertReadable(groupId, userId);
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
        if (!canReadConversation(conversationId, userId)) {
            throw new MessageContextUnavailableException();
        }
    }

    private void assertReadable(String conversationId, String userId) {
        if (!canReadConversation(conversationId, userId)) {
            throw new ForbiddenException("无权访问该会话");
        }
    }

    private boolean canReadConversation(String conversationId, String userId) {
        return conversationId != null && userId != null && (conversationId.startsWith("grp_")
                ? groupMemberService.isGroupMember(conversationId, userId)
                : conversationRepository.findByConversationIdAndUserId(conversationId, userId) != null);
    }

    private MessageReplySnapshot createReplySnapshot(String conversationId, String messageId, String userId) {
        return messageId == null || messageId.isBlank()
                ? null
                : messageReplyService.createSnapshot(conversationId, messageId, userId);
    }

    @Override
    public boolean recallMessage(String messageId, String userId) {
        PrivateMessage privateMessage = privateMessageRepository.findById(messageId).orElse(null);
        if (privateMessage != null) {
            assertRecallable(privateMessage.getSenderId(), privateMessage.getStatus(),
                    privateMessage.getTimestamp() != null ? privateMessage.getTimestamp() : privateMessage.getCreatedAt(),
                    userId, null);
            privateMessage.setStatus(3);
            privateMessage.setUpdatedAt(new Date());
            privateMessageRepository.save(privateMessage);
            messageReplyService.markTargetRecalled(privateMessage.getConversationId(), messageId);
            conversationRecords.onMessageRecalled(privateMessage.getConversationId(), messageId,
                    privateMessage.getSeqId(), privateMessage.getSenderId());
            saveRecallAndNotify(privateMessage.getConversationId(), privateMessage.getSeqId(), messageId, userId,
                    privateMessage.getSenderId(), null,
                    List.of(privateMessage.getSenderId(), privateMessage.getReceiverId()));
            return true;
        }

        GroupMessage groupMessage = groupMessageRepository.findById(messageId).orElse(null);
        if (groupMessage != null) {
            assertRecallable(groupMessage.getSenderId(), groupMessage.getStatus(),
                    groupMessage.getTimestamp() != null ? groupMessage.getTimestamp() : groupMessage.getCreatedAt(),
                    userId, groupMessage.getGroupId());
            groupMessage.setStatus(3);
            groupMessage.setUpdatedAt(new Date());
            groupMessageRepository.save(groupMessage);
            messageReplyService.markTargetRecalled(groupMessage.getGroupId(), messageId);
            conversationRecords.onMessageRecalled(groupMessage.getGroupId(), messageId,
                    groupMessage.getSeqId(), groupMessage.getSenderId());
            String operatorRole = recallOperatorRole(groupMessage.getGroupId(), groupMessage.getSenderId(), userId);
            saveRecallAndNotify(groupMessage.getGroupId(), groupMessage.getSeqId(), messageId, userId,
                    groupMessage.getSenderId(), operatorRole,
                    groupReceiverIds(groupMessage.getGroupId()));
            return true;
        }

        throw MessageRecallException.notFound();
    }

    /**
     * 撤回规则（REST 与 WebSocket 共用）：自己发送的消息，或群主/管理员撤回群内他人消息；
     * 都要求未撤回且仍在撤回时间窗内。私聊只能撤回自己的。
     */
    private void assertRecallable(String senderId, Integer status, Date sentAt, String userId, String groupId) {
        boolean self = userId != null && userId.equals(senderId);
        boolean manager = !self && groupId != null && groupMemberService.isAdminOrOwner(groupId, userId);
        if (!self && !manager) {
            throw new ForbiddenException("只能撤回自己发送的消息");
        }
        if (status != null && status == 3) {
            throw MessageRecallException.alreadyRecalled();
        }
        if (sentAt != null && recallWindowSeconds > 0
                && System.currentTimeMillis() - sentAt.getTime() > recallWindowSeconds * 1000L) {
            throw MessageRecallException.windowExpired(describeWindow(recallWindowSeconds));
        }
    }

    /** 群主/管理员撤回他人消息时带上角色，方便客户端写成“群主/管理员撤回了一条消息”；自己撤回不带。 */
    private String recallOperatorRole(String groupId, String senderId, String userId) {
        if (userId == null || userId.equals(senderId)) {
            return null;
        }
        return groupMemberService.isGroupOwner(groupId, userId) ? "owner" : "admin";
    }

    static String describeWindow(long seconds) {
        return seconds % 60 == 0 ? (seconds / 60) + "分钟" : seconds + "秒";
    }

    private void saveRecallAndNotify(String conversationId, String seqId, String messageId, String userId,
                                     String senderId, String operatorRole, List<String> receiverIds) {
        Date now = new Date();
        RecallLog recallLog = RecallLog.builder()
                .conversationId(conversationId)
                .seqId(seqId)
                .messageId(messageId)
                .operatorId(userId)
                .recallTime(now)
                .build();
        recallLogRepository.save(recallLog);

        // 通过 Redis Stream 实时通知：撤回者收到 RECALL_MESSAGE_RESPONSE，其他人收到 MESSAGE_RECALLED
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message_id", messageId);
        payload.put("conversation_id", conversationId);
        payload.put("recall_user_id", userId);
        payload.put("sender_id", senderId);
        if (operatorRole != null) {
            payload.put("recall_operator_role", operatorRole);
        }
        payload.put("seq_id", seqId);
        payload.put("timestamp", now.getTime());
        redisStreamService.publishRecallLog("RECALL_MESSAGE", userId, serializeMessage(payload),
                receiverIds == null ? List.of() : receiverIds.stream().filter(Objects::nonNull).distinct().toList());
    }

    public long getRecallWindowSeconds() {
        return recallWindowSeconds;
    }

    /**
     * 撤回时间窗（秒），默认 120 秒（2 分钟），配置项 im.message.recall-window-seconds；小于等于 0 表示不限制
     */
    @Value("${im.message.recall-window-seconds:120}")
    public void setRecallWindowSeconds(long recallWindowSeconds) {
        this.recallWindowSeconds = recallWindowSeconds;
    }

    @Override
    public List<MessageResponse> syncMessages(String conversationId, String seqId, String userId) {
        assertReadable(conversationId, userId);
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
            if (!friendService.areFriends(userId, targetId)) {
                throw new ForbiddenException("你们还不是好友，无法发送私聊消息");
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
            message.setReplyTo(createReplySnapshot(conversationId, request.getReplyToMessageId(), userId));
            message.setContentRevision(1);
            message.setClientMessageId(request.getClientMsgId());
            message.setStatus(0);
            message.setSeqId(IdUtil.getSnowflakeNextIdStr());
            message.setTimestamp(new Date());
            message.setCreatedAt(new Date());
            message.setUpdatedAt(new Date());
            applyContent(message::setContent, message::setExt, request, userId, conversationId);

            privateMessageRepository.save(message);
            conversationRecords.onPrivateMessage(message);
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
            message.setReplyTo(createReplySnapshot(targetId, request.getReplyToMessageId(), userId));
            message.setContentRevision(1);
            message.setClientMessageId(request.getClientMsgId());
            message.setStatus(0);
            message.setSeqId(IdUtil.getSnowflakeNextIdStr());

            List<String> memberIds = groupReceiverIds(targetId);
            // 只保留当前群成员（不含自己）的 @；@所有人（all）仅群主/管理员保留
            message.setAtUsers(MentionUtil.sanitize(
                    request.getMessageType(), userId, request.getAtUserIds(), memberIds,
                    groupMemberService.isAdminOrOwner(targetId, userId)));

            message.setTimestamp(new Date());
            message.setCreatedAt(new Date());
            message.setUpdatedAt(new Date());
            applyContent(message::setContent, message::setExt, request, userId, targetId);

            groupMessageRepository.save(message);
            conversationRecords.onGroupMessage(message, memberIds);
            redisStreamService.publishGroupMessage(
                    "GROUP_MESSAGE",
                    userId,
                    serializeMessage(message),
                    memberIds);

            return convertGroupMessageToResponse(message);
        }

        throw new IllegalArgumentException("不支持的接收方ID");
    }

    /**
     * 文本等消息原样保存 content/ext；图片/文件消息的 content 只携带 file_id，由服务端按上传记录补全元数据。
     */
    private void applyContent(java.util.function.Consumer<String> setContent,
                              java.util.function.Consumer<Map<String, Object>> setExt,
                              MessageSendRequest request, String userId, String conversationId) {
        String stored;
        if (ChatMediaService.isMediaMessage(request.getMessageType())) {
            ChatMediaService.ResolvedMedia media = chatMediaService.resolveForMessage(
                    userId, request.getMessageType(), request.getContent());
            setContent.accept(media.content());
            setExt.accept(media.ext());
            stored = media.content();
        } else {
            setContent.accept(request.getContent());
            setExt.accept(request.getExt());
            stored = request.getContent();
        }
        chatMediaService.attachToConversation(stored, conversationId);
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
        // 发送者自己的会话记录必须指向接收方，且会话 ID 与双方一致
        Conversation conversation = conversationRepository.findByConversationIdAndUserId(conversationId, senderId);
        boolean participantsMatch = conversation != null
                && "private".equals(conversation.getConversationType())
                && receiverId.equals(conversation.getTargetId())
                && conversationId.equals(ConversationIdUtil.privateConversationId(senderId, receiverId));
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
        UserBriefResponse senderInfo = senderProfile(message.getSenderId());
        
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
                .content(recalled(message.getStatus()) ? "" : message.getContent())
                .replyTo(recalled(message.getStatus()) ? null : replyReference)
                .forwardedFrom(recalled(message.getStatus()) ? null : forwardedFrom(message.getForwardFrom()))
                .edited(message.getEditedAt() != null)
                .editedAt(formatTimestamp(message.getEditedAt()))
                .contentRevision(message.getContentRevision() == null ? 1 : message.getContentRevision())
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
        UserBriefResponse senderInfo = senderProfile(message.getSenderId());
        
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
                .content(recalled(message.getStatus()) ? "" : message.getContent())
                .replyTo(recalled(message.getStatus()) ? null : replyReference)
                .forwardedFrom(recalled(message.getStatus()) ? null : forwardedFrom(message.getForwardFrom()))
                .edited(message.getEditedAt() != null)
                .editedAt(formatTimestamp(message.getEditedAt()))
                .contentRevision(message.getContentRevision() == null ? 1 : message.getContentRevision())
                .mentionedUserIds(recalled(message.getStatus()) ? null : message.getAtUsers())
                .status(status)
                .deliveryState("sent")
                .seqId(message.getSeqId())
                .clientMessageId(message.getClientMessageId())
                .isRecalled(message.getStatus() == 3)
                .timestamp(formatTimestamp(message.getTimestamp()))
                .build();
    }

    /**
     * 发送者展示信息。昵称为空时用用户名，用户名也没有或用户不存在时用 user_id，避免会话和气泡里出现空白发送者。
     */
    private UserBriefResponse senderProfile(String senderId) {
        UserBriefResponse brief = null;
        try {
            brief = userService.getUserBriefInfoByID(senderId);
        } catch (RuntimeException ignored) {
            brief = null;
        }
        if (brief == null) {
            return UserBriefResponse.builder().userId(senderId).nickname(senderId).build();
        }
        if (brief.getNickname() == null || brief.getNickname().isBlank()) {
            String fallback = brief.getUsername() != null && !brief.getUsername().isBlank()
                    ? brief.getUsername() : senderId;
            brief.setNickname(fallback);
        }
        if (brief.getUserId() == null || brief.getUserId().isBlank()) {
            brief.setUserId(senderId);
        }
        return brief;
    }

    /** 已撤回的消息对任何人（包括发送者）都不再返回原文、@ 列表、引用与转发来源，只保留撤回占位 */
    private static boolean recalled(Integer status) {
        return status != null && status == 3;
    }

    static MessageResponse.ForwardedFrom forwardedFrom(
            icu.tianqingyuluo.onlineim.pojo.document.MessageForwardSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return MessageResponse.ForwardedFrom.builder()
                .userId(snapshot.getOriginalSenderId())
                .displayName(snapshot.getOriginalSenderName())
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
    public List<String> getRecallList(String conversationId, String seqId, String userId) {
        assertReadable(conversationId, userId);
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
