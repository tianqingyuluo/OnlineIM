package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.exception.ReplyTargetUnavailableException;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.MessageReplySnapshot;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReplyReferenceResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.MessageReplyService;
import icu.tianqingyuluo.onlineim.service.UserService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MessageReplyServiceImpl implements MessageReplyService {
    private static final int MAX_PREVIEW_CODE_POINTS = 80;
    private static final int INITIAL_CONTENT_REVISION = 1;

    private final PrivateMessageRepository privateMessageRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final ConversationRepository conversationRepository;
    private final GroupMemberService groupMemberService;
    private final UserService userService;
    private final MongoTemplate mongoTemplate;

    public MessageReplyServiceImpl(PrivateMessageRepository privateMessageRepository,
                                   GroupMessageRepository groupMessageRepository,
                                   ConversationRepository conversationRepository,
                                   GroupMemberService groupMemberService,
                                   UserService userService,
                                   MongoTemplate mongoTemplate) {
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.conversationRepository = conversationRepository;
        this.groupMemberService = groupMemberService;
        this.userService = userService;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public MessageReplySnapshot createSnapshot(String conversationId, String targetMessageId, String userId) {
        if (isBlank(conversationId) || isBlank(targetMessageId) || isBlank(userId)) {
            throw new ReplyTargetUnavailableException();
        }
        assertConversationAccess(conversationId, userId);

        if (conversationId.startsWith("grp_")) {
            GroupMessage target = groupMessageRepository.findByIdAndGroupId(targetMessageId, conversationId);
            if (target == null || isRecalled(target.getStatus())) {
                throw new ReplyTargetUnavailableException();
            }
            return buildSnapshot(
                    target.getId(),
                    target.getSeqId(),
                    target.getSenderId(),
                    target.getMessageType(),
                    target.getContent(),
                    target.getExt(),
                    revision(target.getContentRevision()));
        }

        PrivateMessage target = privateMessageRepository.findByIdAndConversationId(targetMessageId, conversationId);
        if (target == null || isRecalled(target.getStatus())) {
            throw new ReplyTargetUnavailableException();
        }
        return buildSnapshot(
                target.getId(),
                target.getSeqId(),
                target.getSenderId(),
                target.getMessageType(),
                target.getContent(),
                target.getExt(),
                revision(target.getContentRevision()));
    }

    @Override
    public ReplyReferenceResponse resolveForResponse(String conversationId, MessageReplySnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        return resolveBatchForResponses(conversationId, List.of(snapshot)).getFirst();
    }

    @Override
    public List<ReplyReferenceResponse> resolveBatchForResponses(
            String conversationId, List<MessageReplySnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }

        Set<String> targetIds = snapshots.stream()
                .filter(snapshot -> snapshot != null && !isBlank(snapshot.getMessageId()))
                .map(MessageReplySnapshot::getMessageId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, TargetState> targets = loadTargetStates(conversationId, targetIds);
        List<ReplyReferenceResponse> responses = new ArrayList<>(snapshots.size());
        for (MessageReplySnapshot snapshot : snapshots) {
            responses.add(snapshot == null ? null : resolveSnapshot(snapshot, targets.get(snapshot.getMessageId())));
        }
        return responses;
    }

    private Map<String, TargetState> loadTargetStates(String conversationId, Set<String> targetIds) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }

        if (conversationId != null && conversationId.startsWith("grp_")) {
            return groupMessageRepository.findByGroupIdAndIdIn(conversationId, targetIds).stream()
                    .collect(Collectors.toMap(
                            GroupMessage::getId,
                            message -> new TargetState(message.getStatus(), revision(message.getContentRevision())),
                            (left, right) -> left));
        }
        return privateMessageRepository.findByConversationIdAndIdIn(conversationId, targetIds).stream()
                .collect(Collectors.toMap(
                        PrivateMessage::getId,
                        message -> new TargetState(message.getStatus(), revision(message.getContentRevision())),
                        (left, right) -> left));
    }

    private ReplyReferenceResponse resolveSnapshot(MessageReplySnapshot snapshot, TargetState target) {
        String state = snapshot.getState() == null ? "active" : snapshot.getState();
        String previewText = snapshot.getPreviewText();

        if (target == null) {
            state = "unavailable";
            previewText = null;
        } else if (isRecalled(target.status())) {
            state = "recalled";
            previewText = null;
        } else if (target.contentRevision() > revision(snapshot.getContentRevision())) {
            state = "edited";
        }

        if ("recalled".equals(state) || "unavailable".equals(state)) {
            previewText = null;
        }

        return ReplyReferenceResponse.builder()
                .messageId(snapshot.getMessageId())
                .seqId(snapshot.getSeqId())
                .senderId(snapshot.getSenderId())
                .senderDisplayName(snapshot.getSenderDisplayName())
                .messageType(snapshot.getMessageType())
                .previewText(previewText)
                .state(state)
                .build();
    }

    @Override
    public void markTargetRecalled(String conversationId, String messageId) {
        if (isBlank(conversationId) || isBlank(messageId)) {
            return;
        }
        String conversationField = conversationId.startsWith("grp_") ? "groupId" : "conversationId";
        Query query = Query.query(Criteria.where(conversationField).is(conversationId)
                .and("replyTo.messageId").is(messageId));
        Update update = new Update()
                .set("replyTo.state", "recalled")
                .unset("replyTo.previewText");
        Class<?> documentType = conversationId.startsWith("grp_") ? GroupMessage.class : PrivateMessage.class;
        mongoTemplate.updateMulti(query, update, documentType);
    }

    private record TargetState(Integer status, int contentRevision) {
    }

    private MessageReplySnapshot buildSnapshot(String messageId,
                                               String seqId,
                                               String senderId,
                                               String messageType,
                                               String content,
                                               Map<String, Object> ext,
                                               int contentRevision) {
        UserBriefResponse sender = userService.getUserBriefInfoByID(senderId);
        return MessageReplySnapshot.builder()
                .messageId(messageId)
                .seqId(seqId)
                .senderId(senderId)
                .senderDisplayName(displayName(sender, senderId))
                .messageType(messageType)
                .previewText(preview(messageType, content, ext))
                .contentRevision(contentRevision)
                .state("active")
                .build();
    }

    private void assertConversationAccess(String conversationId, String userId) {
        boolean allowed = conversationId.startsWith("grp_")
                ? groupMemberService.isGroupMember(conversationId, userId)
                : conversationRepository.findByIdAndUserIDOrTargetId(conversationId, userId) != null;
        if (!allowed) {
            throw new ReplyTargetUnavailableException();
        }
    }

    private String preview(String messageType, String content, Map<String, Object> ext) {
        String normalizedType = messageType == null ? "" : messageType.toLowerCase(Locale.ROOT);
        return switch (normalizedType) {
            case "text" -> truncate(normalizeText(content));
            case "image" -> "[图片]";
            case "voice", "audio" -> "[语音]";
            case "video" -> "[视频]";
            case "emoji", "sticker" -> "[表情]";
            case "file" -> filePreview(ext);
            default -> "[消息]";
        };
    }

    private String filePreview(Map<String, Object> ext) {
        Object rawName = ext == null ? null : ext.get("file_name");
        if (rawName instanceof String fileName && !fileName.isBlank()) {
            return "[文件] " + truncate(fileName.strip());
        }
        return "[文件]";
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private String truncate(String value) {
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= MAX_PREVIEW_CODE_POINTS) {
            return value;
        }
        int end = value.offsetByCodePoints(0, MAX_PREVIEW_CODE_POINTS);
        return value.substring(0, end) + "…";
    }

    private String displayName(UserBriefResponse user, String fallback) {
        if (user == null) {
            return fallback;
        }
        if (!isBlank(user.getNickname())) {
            return user.getNickname();
        }
        if (!isBlank(user.getUsername())) {
            return user.getUsername();
        }
        return fallback;
    }

    private int revision(Integer value) {
        return value == null || value < INITIAL_CONTENT_REVISION ? INITIAL_CONTENT_REVISION : value;
    }

    private boolean isRecalled(Integer status) {
        return status != null && status == 3;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
