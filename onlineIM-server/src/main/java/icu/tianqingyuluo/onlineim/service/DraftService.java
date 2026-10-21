package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.DraftException;
import icu.tianqingyuluo.onlineim.exception.ReplyTargetUnavailableException;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.ConversationDraft;
import icu.tianqingyuluo.onlineim.pojo.document.MessageReplySnapshot;
import icu.tianqingyuluo.onlineim.pojo.dto.request.DraftSaveRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.DraftMentionResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.DraftResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReplyReferenceResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationDraftRepository;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import icu.tianqingyuluo.onlineim.util.DraftPreview;
import icu.tianqingyuluo.onlineim.util.MentionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会话草稿：每个用户每个会话一份，存在 MongoDB。后一次保存覆盖前一次。
 * 保存成功后经 Redis Stream 给该用户自己的在线连接推送 {@code DRAFT_UPDATED}（多端刷新输入框和会话列表）。
 * 引用目标不可用时丢掉引用、仍保存文本；群 @ 按发送时的清洗规则静默剔除无效成员。
 */
@Slf4j
@Service
public class DraftService {

    public static final String STREAM_TYPE = "DRAFT_UPDATED";
    public static final int MAX_NAME_LENGTH = 64;

    private final ConversationDraftRepository draftRepository;
    private final ConversationRepository conversationRepository;
    private final FriendService friendService;
    private final GroupMemberService groupMemberService;
    private final MessageReplyService messageReplyService;
    private final RedisStreamService redisStreamService;
    private final ObjectMapper objectMapper;
    private final int maxLength;

    public DraftService(ConversationDraftRepository draftRepository,
                        ConversationRepository conversationRepository,
                        FriendService friendService,
                        GroupMemberService groupMemberService,
                        MessageReplyService messageReplyService,
                        RedisStreamService redisStreamService,
                        ObjectMapper objectMapper,
                        @Value("${im.draft.max-length:5000}") int maxLength) {
        this.draftRepository = draftRepository;
        this.conversationRepository = conversationRepository;
        this.friendService = friendService;
        this.groupMemberService = groupMemberService;
        this.messageReplyService = messageReplyService;
        this.redisStreamService = redisStreamService;
        this.objectMapper = objectMapper;
        this.maxLength = maxLength;
    }

    /**
     * 保存草稿。内容为空白时删除并返回 null。内容与已存草稿完全一致时不写库、不推送。
     */
    public DraftResponse save(String conversationId, String userId, DraftSaveRequest request) {
        assertMember(conversationId, userId);
        String content = request == null || request.getContent() == null ? "" : request.getContent();
        if (content.length() > maxLength) {
            throw DraftException.contentTooLong(maxLength);
        }
        if (content.isBlank()) {
            clear(conversationId, userId);
            return null;
        }

        MessageReplySnapshot reply = resolveReply(conversationId, userId,
                request == null ? null : request.getReplyToMessageId());
        List<ConversationDraft.Mention> mentions = sanitizeMentions(conversationId, userId,
                request == null ? null : request.getAtUsers());

        ConversationDraft existing = draftRepository.findById(ConversationDraft.documentId(userId, conversationId))
                .orElse(null);
        if (existing != null && same(existing, content, reply, mentions)) {
            return toResponse(existing);
        }

        ConversationDraft draft = ConversationDraft.builder()
                .id(ConversationDraft.documentId(userId, conversationId))
                .userId(userId)
                .conversationId(conversationId)
                .content(content)
                .replyTo(reply)
                .atUsers(mentions)
                .updatedAt(System.currentTimeMillis())
                .build();
        draftRepository.save(draft);
        DraftResponse response = toResponse(draft);
        publish(userId, response, false);
        return response;
    }

    /** 没有草稿时返回 null */
    public DraftResponse get(String conversationId, String userId) {
        assertMember(conversationId, userId);
        return draftRepository.findById(ConversationDraft.documentId(userId, conversationId))
                .filter(draft -> draft.getContent() != null && !draft.getContent().isBlank())
                .map(this::toResponse)
                .orElse(null);
    }

    /** 删除草稿。本来就没有时不推送。 */
    public void clear(String conversationId, String userId) {
        assertMember(conversationId, userId);
        String id = ConversationDraft.documentId(userId, conversationId);
        if (draftRepository.findById(id).isEmpty()) {
            return;
        }
        draftRepository.deleteById(id);
        DraftResponse cleared = DraftResponse.builder()
                .conversationId(conversationId)
                .updatedAt(System.currentTimeMillis())
                .cleared(true)
                .build();
        publish(userId, cleared, true);
    }

    /** 会话列表用的摘要：conversationId → 折叠后的预览。没有草稿的会话不在 map 里。 */
    public Map<String, String> previewsOf(String userId) {
        Map<String, String> previews = new LinkedHashMap<>();
        for (ConversationDraft draft : draftRepository.findByUserId(userId)) {
            String preview = DraftPreview.of(draft.getContent());
            if (!preview.isEmpty() && draft.getConversationId() != null) {
                previews.put(draft.getConversationId(), preview);
            }
        }
        return previews;
    }

    public String previewOf(String userId, String conversationId) {
        if (userId == null || conversationId == null) {
            return null;
        }
        return draftRepository.findById(ConversationDraft.documentId(userId, conversationId))
                .map(draft -> {
                    String preview = DraftPreview.of(draft.getContent());
                    return preview.isEmpty() ? null : preview;
                })
                .orElse(null);
    }

    private void assertMember(String conversationId, String userId) {
        if (conversationId == null || conversationId.isBlank() || userId == null || userId.isBlank()) {
            throw DraftException.notMember();
        }
        if (conversationId.startsWith("grp_")) {
            if (!groupMemberService.isGroupMember(conversationId, userId)) {
                throw DraftException.notMember();
            }
            return;
        }
        Conversation own = conversationRepository.findByConversationIdAndUserId(conversationId, userId);
        if (own != null) {
            return;
        }
        String peer = ConversationIdUtil.otherParticipant(conversationId, userId);
        if (peer != null
                && conversationId.equals(ConversationIdUtil.privateConversationId(userId, peer))
                && friendService.areFriends(userId, peer)) {
            return;
        }
        throw DraftException.notMember();
    }

    private MessageReplySnapshot resolveReply(String conversationId, String userId, String replyToMessageId) {
        if (replyToMessageId == null || replyToMessageId.isBlank()) {
            return null;
        }
        try {
            return messageReplyService.createSnapshot(conversationId, replyToMessageId, userId);
        } catch (ReplyTargetUnavailableException ex) {
            log.info("草稿引用目标不可用，仅保存文本: conversation={}, reply={}", conversationId, replyToMessageId);
            return null;
        }
    }

    private List<ConversationDraft.Mention> sanitizeMentions(String conversationId, String userId,
                                                             List<DraftSaveRequest.Mention> requested) {
        if (!conversationId.startsWith("grp_") || requested == null || requested.isEmpty()) {
            return null;
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (DraftSaveRequest.Mention mention : requested) {
            if (mention == null || mention.getUserId() == null || mention.getUserId().isBlank()) {
                continue;
            }
            String name = cleanName(mention.getName());
            if (name.isEmpty() || names.containsKey(mention.getUserId())) {
                continue;
            }
            names.put(mention.getUserId(), name);
        }
        List<String> memberIds = groupMemberService.getGroupMembers(conversationId).stream()
                .map(GroupMemberResponse::getUserInfo)
                .filter(Objects::nonNull)
                .map(user -> user.getUserId())
                .filter(Objects::nonNull)
                .toList();
        List<String> kept = MentionUtil.sanitize("text", userId, names.keySet(), memberIds);
        if (kept == null) {
            return null;
        }
        List<ConversationDraft.Mention> mentions = new ArrayList<>();
        for (String id : kept) {
            mentions.add(ConversationDraft.Mention.builder().userId(id).name(names.get(id)).build());
        }
        return mentions;
    }

    private static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replaceAll("\\p{Cntrl}", "").trim();
        int count = cleaned.codePointCount(0, cleaned.length());
        if (count <= MAX_NAME_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_NAME_LENGTH));
    }

    private boolean same(ConversationDraft existing, String content, MessageReplySnapshot reply,
                         List<ConversationDraft.Mention> mentions) {
        if (!content.equals(existing.getContent())) {
            return false;
        }
        String oldReply = existing.getReplyTo() == null ? null : existing.getReplyTo().getMessageId();
        String newReply = reply == null ? null : reply.getMessageId();
        if (!Objects.equals(oldReply, newReply)) {
            return false;
        }
        return mentionKey(existing.getAtUsers()).equals(mentionKey(mentions));
    }

    private static List<String> mentionKey(List<ConversationDraft.Mention> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return List.of();
        }
        return mentions.stream()
                .map(mention -> mention.getUserId() + "\n" + mention.getName())
                .toList();
    }

    private DraftResponse toResponse(ConversationDraft draft) {
        ReplyReferenceResponse reply = draft.getReplyTo() == null
                ? null
                : messageReplyService.resolveForResponse(draft.getConversationId(), draft.getReplyTo());
        List<DraftMentionResponse> mentions = null;
        if (draft.getAtUsers() != null && !draft.getAtUsers().isEmpty()) {
            mentions = new ArrayList<>();
            for (ConversationDraft.Mention mention : draft.getAtUsers()) {
                mentions.add(DraftMentionResponse.builder()
                        .userId(mention.getUserId())
                        .name(mention.getName())
                        .build());
            }
        }
        return DraftResponse.builder()
                .conversationId(draft.getConversationId())
                .content(draft.getContent())
                .draftPreview(DraftPreview.of(draft.getContent()))
                .replyToMessageId(reply == null ? null : reply.getMessageId())
                .replyTo(reply)
                .atUsers(mentions)
                .updatedAt(draft.getUpdatedAt())
                .build();
    }

    private void publish(String userId, DraftResponse response, boolean cleared) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("conversation_id", response.getConversationId());
            payload.put("content", cleared ? null : response.getContent());
            payload.put("draft_preview", cleared ? null : response.getDraftPreview());
            payload.put("reply_to_message_id", cleared ? null : response.getReplyToMessageId());
            payload.put("reply_to", cleared ? null : response.getReplyTo());
            payload.put("at_users", cleared ? List.of() : response.getAtUsers());
            payload.put("updated_at", response.getUpdatedAt());
            payload.put("cleared", cleared);
            redisStreamService.publishPrivateMessage(STREAM_TYPE, userId,
                    objectMapper.writeValueAsString(payload), userId);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("草稿已保存，但多端通知发送失败: conversation={}, err={}",
                    response.getConversationId(), ex.getMessage());
        }
    }
}
