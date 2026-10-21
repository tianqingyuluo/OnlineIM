package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.MessageEditException;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.MessageEditRecord;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageEditRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.util.MentionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/**
 * 文本消息编辑（REST PUT /api/v1/messages/{id} 与 WebSocket EDIT_MESSAGE_REQUEST 共用）。
 *
 * <p>规则：只能编辑自己发送的、未撤回的文本消息，且必须在编辑时间窗内（默认与撤回时间窗一致，2 分钟）；
 * 私聊要求双方仍是好友，群聊要求仍是群成员。新内容去掉首尾空白后不能为空、不能超过长度上限；
 * 内容与 @ 列表都没变时直接返回当前消息，不产生新版本、不推送。</p>
 *
 * <p>编辑成功：content 替换为新内容，contentRevision + 1，记录 editedAt，旧版本追加到只在服务端保存的
 * editHistory（[0] 为原文）；会话列表里如果最后一条是它，预览同步更新；
 * 经 Redis Stream 推送 EDIT_MESSAGE，所有在线的会话成员（含编辑者自己）收到 MESSAGE_EDITED。
 * 引用了它的回复保留引用时的快照，按已有逻辑显示“原消息已编辑”。</p>
 */
@Slf4j
@Service
public class MessageEditService {

    public static final String STREAM_TYPE = "EDIT_MESSAGE";
    /** editHistory 最多保存的旧版本数（原文始终保留） */
    static final int MAX_HISTORY = 20;

    private final PrivateMessageRepository privateMessageRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final MongoTemplate mongoTemplate;
    private final FriendService friendService;
    private final GroupMemberService groupMemberService;
    private final ConversationRecordService conversationRecords;
    private final RedisStreamService redisStreamService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final long editWindowSeconds;
    private final int maxLength;

    public MessageEditService(PrivateMessageRepository privateMessageRepository,
                              GroupMessageRepository groupMessageRepository,
                              MongoTemplate mongoTemplate,
                              FriendService friendService,
                              GroupMemberService groupMemberService,
                              ConversationRecordService conversationRecords,
                              RedisStreamService redisStreamService,
                              MessageService messageService,
                              ObjectMapper objectMapper,
                              @Value("${im.message.edit-window-seconds:${im.message.recall-window-seconds:120}}") long editWindowSeconds,
                              @Value("${im.message.edit-max-length:5000}") int maxLength) {
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.mongoTemplate = mongoTemplate;
        this.friendService = friendService;
        this.groupMemberService = groupMemberService;
        this.conversationRecords = conversationRecords;
        this.redisStreamService = redisStreamService;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
        this.editWindowSeconds = editWindowSeconds;
        this.maxLength = maxLength;
    }

    public long getEditWindowSeconds() {
        return editWindowSeconds;
    }

    public MessageResponse edit(String messageId, MessageEditRequest request, String userId) {
        if (messageId == null || messageId.isBlank()) {
            throw MessageEditException.notFound();
        }
        String content = normalizeContent(request == null ? null : request.getContent());
        List<String> requestedAtUsers = request == null ? null : request.getAtUsers();

        PrivateMessage privateMessage = privateMessageRepository.findById(messageId).orElse(null);
        if (privateMessage != null) {
            return editPrivate(privateMessage, content, userId);
        }
        GroupMessage groupMessage = groupMessageRepository.findById(messageId).orElse(null);
        if (groupMessage != null) {
            return editGroup(groupMessage, content, requestedAtUsers, userId);
        }
        throw MessageEditException.notFound();
    }

    /** 读取一条消息并按历史接口格式返回（推送 MESSAGE_EDITED 时使用），找不到返回 null */
    public MessageResponse loadResponse(String conversationId, String messageId) {
        if (conversationId == null || messageId == null) {
            return null;
        }
        if (conversationId.startsWith("grp_")) {
            GroupMessage message = groupMessageRepository.findByIdAndGroupId(messageId, conversationId);
            return message == null ? null : messageService.convertGroupMessageToResponse(message);
        }
        PrivateMessage message = privateMessageRepository.findByIdAndConversationId(messageId, conversationId);
        return message == null ? null : messageService.convertPrivateMessageToResponse(message);
    }

    private MessageResponse editPrivate(PrivateMessage message, String content, String userId) {
        assertEditable(message.getSenderId(), message.getMessageType(), message.getStatus(),
                message.getTimestamp() != null ? message.getTimestamp() : message.getCreatedAt(), userId);
        if (!friendService.areFriends(userId, message.getReceiverId())) {
            throw MessageEditException.notMember("你们已不是好友，无法编辑这条消息");
        }
        if (content.equals(message.getContent())) {
            return messageService.convertPrivateMessageToResponse(message);
        }
        Date now = new Date();
        int revision = revision(message.getContentRevision());
        List<MessageEditRecord> history = appendHistory(message.getEditHistory(), revision,
                message.getContent(), null, now);
        Update update = new Update()
                .set("content", content)
                .set("contentRevision", revision + 1)
                .set("editedAt", now)
                .set("updatedAt", now)
                .set("editHistory", history);
        if (mongoTemplate.updateFirst(revisionQuery(message.getId(), message.getContentRevision()), update,
                PrivateMessage.class).getModifiedCount() == 0) {
            throw MessageEditException.conflict();
        }
        message.setContent(content);
        message.setContentRevision(revision + 1);
        message.setEditedAt(now);
        message.setUpdatedAt(now);
        message.setEditHistory(history);

        conversationRecords.onMessageEdited(message.getConversationId(), message.getId(),
                message.getMessageType(), content);
        publish(message.getConversationId(), message.getId(), revision + 1, userId,
                List.of(message.getSenderId(), message.getReceiverId()));
        return messageService.convertPrivateMessageToResponse(message);
    }

    private MessageResponse editGroup(GroupMessage message, String content, List<String> requestedAtUsers,
                                      String userId) {
        assertEditable(message.getSenderId(), message.getMessageType(), message.getStatus(),
                message.getTimestamp() != null ? message.getTimestamp() : message.getCreatedAt(), userId);
        if (!groupMemberService.isGroupMember(message.getGroupId(), userId)) {
            throw MessageEditException.notMember("你已不在该群，无法编辑这条消息");
        }
        List<String> memberIds = memberIds(message.getGroupId());
        List<String> previousAtUsers = message.getAtUsers() == null ? List.of() : message.getAtUsers();
        boolean canMentionAll = groupMemberService.isAdminOrOwner(message.getGroupId(), message.getSenderId());
        List<String> atUsers = requestedAtUsers == null
                ? MentionUtil.sanitize(message.getMessageType(), message.getSenderId(), previousAtUsers, memberIds, canMentionAll)
                : MentionUtil.sanitize(message.getMessageType(), message.getSenderId(), requestedAtUsers, memberIds, canMentionAll);
        List<String> nextAtUsers = atUsers == null ? List.of() : atUsers;
        if (content.equals(message.getContent()) && new LinkedHashSet<>(nextAtUsers).equals(new LinkedHashSet<>(previousAtUsers))) {
            return messageService.convertGroupMessageToResponse(message);
        }

        Date now = new Date();
        int revision = revision(message.getContentRevision());
        List<MessageEditRecord> history = appendHistory(message.getEditHistory(), revision,
                message.getContent(), message.getAtUsers(), now);
        Update update = new Update()
                .set("content", content)
                .set("contentRevision", revision + 1)
                .set("editedAt", now)
                .set("updatedAt", now)
                .set("editHistory", history);
        if (atUsers == null) {
            update.unset("atUsers");
        } else {
            update.set("atUsers", atUsers);
        }
        if (mongoTemplate.updateFirst(revisionQuery(message.getId(), message.getContentRevision()), update,
                GroupMessage.class).getModifiedCount() == 0) {
            throw MessageEditException.conflict();
        }
        message.setContent(content);
        message.setContentRevision(revision + 1);
        message.setEditedAt(now);
        message.setUpdatedAt(now);
        message.setEditHistory(history);
        message.setAtUsers(atUsers);

        if (!new LinkedHashSet<>(previousAtUsers).equals(new LinkedHashSet<>(nextAtUsers))) {
            // all 与具体用户不能做差集，按当前列表整表重写
            conversationRecords.replaceMentions(message, memberIds);
        }
        conversationRecords.onMessageEdited(message.getGroupId(), message.getId(), message.getMessageType(), content);

        List<String> receivers = new ArrayList<>(memberIds);
        if (!receivers.contains(userId)) {
            receivers.add(userId);
        }
        publish(message.getGroupId(), message.getId(), revision + 1, userId, receivers);
        return messageService.convertGroupMessageToResponse(message);
    }

    /**
     * 编辑规则：先确认是自己的消息，再看是否已撤回、是否是文本、是否超过时间窗。
     */
    void assertEditable(String senderId, String messageType, Integer status, Date sentAt, String userId) {
        if (userId == null || !userId.equals(senderId)) {
            throw MessageEditException.notSender();
        }
        if (status != null && status == 3) {
            throw MessageEditException.recalled();
        }
        if (messageType != null && !"text".equalsIgnoreCase(messageType)) {
            throw MessageEditException.notSupported();
        }
        if (sentAt != null && editWindowSeconds > 0
                && System.currentTimeMillis() - sentAt.getTime() > editWindowSeconds * 1000L) {
            throw MessageEditException.windowExpired(describeWindow(editWindowSeconds));
        }
    }

    String normalizeContent(String content) {
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.isEmpty()) {
            throw MessageEditException.invalidContent("编辑后的内容不能为空");
        }
        if (maxLength > 0 && trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            throw MessageEditException.invalidContent("编辑后的内容不能超过" + maxLength + "个字符");
        }
        return trimmed;
    }

    static String describeWindow(long seconds) {
        return seconds % 60 == 0 ? (seconds / 60) + "分钟" : seconds + "秒";
    }

    static List<MessageEditRecord> appendHistory(List<MessageEditRecord> existing, int revision, String content,
                                                 List<String> atUsers, Date replacedAt) {
        List<MessageEditRecord> history = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        history.add(MessageEditRecord.builder()
                .revision(revision)
                .content(content)
                .atUsers(atUsers == null ? null : List.copyOf(atUsers))
                .replacedAt(replacedAt)
                .build());
        // 原文（[0]）始终保留，超出上限时丢掉最早的中间版本
        while (history.size() > MAX_HISTORY) {
            history.remove(1);
        }
        return history;
    }

    private static int revision(Integer revision) {
        return revision == null || revision < 1 ? 1 : revision;
    }

    /** 乐观锁：只有在版本没被别人改过时才写入（历史消息缺 contentRevision 时按 1 处理） */
    private static Query revisionQuery(String messageId, Integer currentRevision) {
        Criteria criteria = Criteria.where("_id").is(messageId);
        if (currentRevision == null) {
            criteria = criteria.and("contentRevision").is(null);
        } else {
            criteria = criteria.and("contentRevision").is(currentRevision);
        }
        return Query.query(criteria);
    }

    private List<String> memberIds(String groupId) {
        List<GroupMemberResponse> members = groupMemberService.getGroupMembers(groupId);
        if (members == null) {
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

    private void publish(String conversationId, String messageId, int revision, String editorId,
                         List<String> receivers) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message_id", messageId);
        payload.put("conversation_id", conversationId);
        payload.put("content_revision", revision);
        payload.put("editor_id", editorId);
        try {
            redisStreamService.publishGroupMessage(STREAM_TYPE, editorId, objectMapper.writeValueAsString(payload),
                    receivers.stream().filter(Objects::nonNull).distinct().toList());
        } catch (JsonProcessingException | RuntimeException e) {
            // 编辑已落库，推送失败只影响实时性：客户端重新打开会话时会从历史接口拿到新内容
            log.warn("推送消息编辑通知失败: messageId={}, error={}", messageId, e.getMessage());
        }
    }
}
