package icu.tianqingyuluo.onlineim.service;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.MessageForwardException;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.MessageForwardSnapshot;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageForwardRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageForwardResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import icu.tianqingyuluo.onlineim.util.E2eeEnvelope;
import icu.tianqingyuluo.onlineim.util.SeqIdComparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * 消息转发（POST /api/v1/messages/forward）。
 *
 * <ul>
 *   <li>separate：每条来源消息在每个目标会话里成为一条新消息，保留类型与内容，带 forwarded_from（原作者）。</li>
 *   <li>merged：所有来源消息打包为一条 chat_record（聊天记录）消息，content 内含条目快照。</li>
 * </ul>
 * 整个请求先全部校验（来源会话参与者、消息存在且未撤回、目标是好友或已加入的群、数量上限），
 * 任一不通过即整体拒绝，不产生任何消息。媒体按 file_id 引用复用，元数据由服务端从上传记录重新生成。
 * 新消息与普通发送走相同的会话记录更新与 Redis 推送，接收方收到标准的 *_MESSAGE_RESPONSE 帧。
 */
@Slf4j
@Service
public class MessageForwardService {

    public static final String MODE_SEPARATE = "separate";
    public static final String MODE_MERGED = "merged";
    public static final String CHAT_RECORD = "chat_record";
    static final int RECALLED = 3;
    static final int MAX_RECORD_CONTENT_CHARS = 512 * 1024;

    private static final Set<String> FORWARDABLE_TYPES = Set.of(
            "text", "image", "file", "voice", "audio", "video", "emoji", "sticker", CHAT_RECORD);
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final PrivateMessageRepository privateMessageRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final ConversationRepository conversationRepository;
    private final FriendService friendService;
    private final GroupMemberService groupMemberService;
    private final GroupService groupService;
    private final UserService userService;
    private final ChatMediaService chatMediaService;
    private final ConversationRecordService conversationRecords;
    private final RedisStreamService redisStreamService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final int maxTargets;
    private final int maxSeparateMessages;
    private final int maxMergedMessages;

    public MessageForwardService(PrivateMessageRepository privateMessageRepository,
                                 GroupMessageRepository groupMessageRepository,
                                 ConversationRepository conversationRepository,
                                 FriendService friendService,
                                 GroupMemberService groupMemberService,
                                 GroupService groupService,
                                 UserService userService,
                                 ChatMediaService chatMediaService,
                                 ConversationRecordService conversationRecords,
                                 RedisStreamService redisStreamService,
                                 MessageService messageService,
                                 ObjectMapper objectMapper,
                                 @Value("${im.forward.max-targets:9}") int maxTargets,
                                 @Value("${im.forward.max-messages:30}") int maxSeparateMessages,
                                 @Value("${im.forward.max-merged-messages:100}") int maxMergedMessages) {
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.conversationRepository = conversationRepository;
        this.friendService = friendService;
        this.groupMemberService = groupMemberService;
        this.groupService = groupService;
        this.userService = userService;
        this.chatMediaService = chatMediaService;
        this.conversationRecords = conversationRecords;
        this.redisStreamService = redisStreamService;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
        this.maxTargets = maxTargets;
        this.maxSeparateMessages = maxSeparateMessages;
        this.maxMergedMessages = maxMergedMessages;
    }

    /** 统一的来源消息视图（私聊 / 群聊） */
    record Source(String id, String senderId, String messageType, String content, Map<String, Object> ext,
                  String seqId, Date timestamp, Integer status, MessageForwardSnapshot forwardFrom,
                  Boolean encrypted) {
    }

    /** 准备写入目标会话的内容 */
    record Payload(String key, String messageType, String content, Map<String, Object> ext,
                   MessageForwardSnapshot forwardFrom) {
    }

    public MessageForwardResponse forward(MessageForwardRequest request, String userId) {
        if (userId == null || userId.isBlank()) {
            throw MessageForwardException.forbidden("用户未登录");
        }
        if (request == null) {
            throw MessageForwardException.badRequest("转发请求不能为空");
        }
        String mode = request.getMode() == null || request.getMode().isBlank()
                ? MODE_SEPARATE : request.getMode().trim();
        if (!MODE_SEPARATE.equals(mode) && !MODE_MERGED.equals(mode)) {
            throw MessageForwardException.badRequest("不支持的转发方式：" + mode);
        }
        String sourceId = trimToNull(request.getSourceConversationId());
        if (sourceId == null) {
            throw MessageForwardException.badRequest("来源会话不能为空");
        }
        List<String> messageIds = distinct(request.getMessageIds());
        if (messageIds.isEmpty()) {
            throw MessageForwardException.badRequest("请选择要转发的消息");
        }
        int maxMessages = MODE_MERGED.equals(mode) ? maxMergedMessages : maxSeparateMessages;
        if (messageIds.size() > maxMessages) {
            throw MessageForwardException.badRequest(
                    (MODE_MERGED.equals(mode) ? "合并转发" : "逐条转发") + "最多 " + maxMessages + " 条消息");
        }
        List<String> targets = distinct(request.getTargets());
        if (targets.isEmpty()) {
            throw MessageForwardException.badRequest("请选择转发目标");
        }
        if (targets.size() > maxTargets) {
            throw MessageForwardException.badRequest("一次最多转发给 " + maxTargets + " 个会话");
        }
        String requestId = trimToNull(request.getClientRequestId());
        if (requestId == null) {
            requestId = IdUtil.fastSimpleUUID();
        } else if (!REQUEST_ID.matcher(requestId).matches()) {
            throw MessageForwardException.badRequest("client_request_id 格式不正确");
        }

        // 1. 来源会话：转发者必须是参与者
        boolean groupSource = sourceId.startsWith("grp_");
        assertSourceReadable(sourceId, groupSource, userId);

        // 2. 来源消息：必须都在该会话中、未撤回、类型可转发
        List<Source> sources = loadSources(sourceId, groupSource, messageIds, userId);

        // 3. 目标：好友或已加入的群
        for (String target : targets) {
            assertTargetWritable(target, userId);
        }

        // 4. 生成内容（媒体按引用复用，元数据由服务端填充）
        Map<String, String> names = new HashMap<>();
        List<Payload> payloads = MODE_MERGED.equals(mode)
                ? List.of(mergedPayload(sourceId, groupSource, sources, userId, names))
                : separatePayloads(sourceId, sources, userId, names);

        // 5. 写入每个目标
        List<MessageForwardResponse.TargetResult> results = new ArrayList<>();
        for (String target : targets) {
            List<MessageResponse> created = new ArrayList<>();
            String conversationId = target.startsWith("grp_")
                    ? target : ConversationIdUtil.privateConversationId(userId, target);
            for (Payload payload : payloads) {
                String clientMessageId = "fwd:" + requestId + ":" + target + ":" + payload.key();
                created.add(target.startsWith("grp_")
                        ? sendToGroup(target, userId, payload, clientMessageId)
                        : sendToFriend(target, conversationId, userId, payload, clientMessageId));
            }
            results.add(MessageForwardResponse.TargetResult.builder()
                    .targetId(target)
                    .conversationId(conversationId)
                    .messages(created)
                    .build());
        }
        log.info("用户 {} 从 {} 转发 {} 条消息到 {} 个会话（{}）",
                userId, sourceId, sources.size(), targets.size(), mode);
        return MessageForwardResponse.builder().mode(mode).results(results).build();
    }

    private void assertSourceReadable(String sourceId, boolean groupSource, String userId) {
        boolean readable = groupSource
                ? groupMemberService.isGroupMember(sourceId, userId)
                : conversationRepository.findByConversationIdAndUserId(sourceId, userId) != null;
        if (!readable) {
            throw MessageForwardException.forbidden("无权访问来源会话");
        }
    }

    private List<Source> loadSources(String sourceId, boolean groupSource, List<String> ids, String userId) {
        List<Source> found = new ArrayList<>();
        if (groupSource) {
            for (GroupMessage m : groupMessageRepository.findByGroupIdAndIdIn(sourceId, ids)) {
                found.add(new Source(m.getId(), m.getSenderId(), m.getMessageType(), m.getContent(), m.getExt(),
                        m.getSeqId(), m.getTimestamp(), m.getStatus(), m.getForwardFrom(), null));
            }
        } else {
            for (PrivateMessage m : privateMessageRepository.findByConversationIdAndIdIn(sourceId, ids)) {
                if (!userId.equals(m.getSenderId()) && !userId.equals(m.getReceiverId())) {
                    continue;
                }
                found.add(new Source(m.getId(), m.getSenderId(), m.getMessageType(), m.getContent(), m.getExt(),
                        m.getSeqId(), m.getTimestamp(), m.getStatus(), m.getForwardFrom(), m.getIsEncrypted()));
            }
        }
        if (found.size() != ids.size()) {
            throw MessageForwardException.notFound("部分消息不存在或不属于该会话");
        }
        for (Source source : found) {
            if (source.status() != null && source.status() == RECALLED) {
                throw MessageForwardException.recalled("已撤回的消息不能转发");
            }
            if (source.messageType() == null || !FORWARDABLE_TYPES.contains(source.messageType())) {
                throw MessageForwardException.badRequest("该类型的消息不能转发");
            }
            if (E2eeEnvelope.isEncrypted(source.encrypted(), source.content())) {
                throw MessageForwardException.badRequest("端到端加密消息不能转发，请在原会话中查看");
            }
        }
        found.sort((a, b) -> compareSeq(a.seqId(), b.seqId()));
        return found;
    }

    private static int compareSeq(String a, String b) {
        try {
            return SeqIdComparator.compare(a, b);
        } catch (RuntimeException e) {
            return String.valueOf(a).compareTo(String.valueOf(b));
        }
    }

    private void assertTargetWritable(String target, String userId) {
        if (target.startsWith("usr_")) {
            if (target.equals(userId)) {
                throw MessageForwardException.badRequest("不能转发给自己");
            }
            if (!friendService.areFriends(userId, target)) {
                throw MessageForwardException.forbidden("只能转发给好友");
            }
        } else if (target.startsWith("grp_")) {
            if (!groupMemberService.isGroupMember(target, userId)) {
                throw MessageForwardException.forbidden("只能转发到你所在的群聊");
            }
        } else {
            throw MessageForwardException.badRequest("不支持的转发目标：" + target);
        }
    }

    private List<Payload> separatePayloads(String sourceId, List<Source> sources, String userId,
                                           Map<String, String> names) {
        List<Payload> payloads = new ArrayList<>();
        for (Source source : sources) {
            ChatMediaService.ResolvedMedia media = refreshed(source);
            MessageForwardSnapshot origin = source.forwardFrom();
            // 转发链保留最初作者
            MessageForwardSnapshot snapshot = MessageForwardSnapshot.builder()
                    .originalSenderId(origin != null && origin.getOriginalSenderId() != null
                            ? origin.getOriginalSenderId() : source.senderId())
                    .originalSenderName(origin != null && origin.getOriginalSenderName() != null
                            ? origin.getOriginalSenderName() : displayName(source.senderId(), names))
                    .sourceConversationId(sourceId)
                    .sourceMessageId(source.id())
                    .forwardedBy(userId)
                    .build();
            payloads.add(new Payload(source.id(), source.messageType(), media.content(), media.ext(), snapshot));
        }
        return payloads;
    }

    private Payload mergedPayload(String sourceId, boolean groupSource, List<Source> sources, String userId,
                                  Map<String, String> names) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, String> avatars = new HashMap<>();
        for (Source source : sources) {
            ChatMediaService.ResolvedMedia media = refreshed(source);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sender_id", source.senderId());
            item.put("sender_name", displayName(source.senderId(), names));
            String avatar = avatars.computeIfAbsent(source.senderId(), id -> {
                UserBriefResponse brief = brief(id);
                return brief == null || brief.getAvatarUrl() == null ? "" : brief.getAvatarUrl();
            });
            if (!avatar.isEmpty()) {
                item.put("sender_avatar", avatar);
            }
            item.put("message_type", source.messageType());
            item.put("content", media.content());
            item.put("timestamp", formatTimestamp(source.timestamp()));
            if (source.forwardFrom() != null && source.forwardFrom().getOriginalSenderId() != null) {
                item.put("forwarded_from", Map.of(
                        "user_id", source.forwardFrom().getOriginalSenderId(),
                        "display_name", Objects.toString(source.forwardFrom().getOriginalSenderName(), "")));
            }
            items.add(item);
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("title", recordTitle(sourceId, groupSource, sources, userId, names));
        record.put("count", items.size());
        record.put("items", items);
        String content;
        try {
            content = objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("聊天记录序列化失败", e);
        }
        if (content.length() > MAX_RECORD_CONTENT_CHARS) {
            throw MessageForwardException.badRequest("聊天记录内容过大，请减少消息数量");
        }
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("record_count", items.size());
        return new Payload("merged", CHAT_RECORD, content, ext, null);
    }

    private String recordTitle(String sourceId, boolean groupSource, List<Source> sources, String userId,
                               Map<String, String> names) {
        if (groupSource) {
            String groupName = null;
            try {
                GroupBriefResponse group = groupService.getGroupByID(sourceId);
                groupName = group == null ? null : group.getName();
            } catch (RuntimeException e) {
                log.debug("读取群名失败 {}", sourceId, e);
            }
            return (groupName == null || groupName.isBlank() ? "群聊" : "群聊“" + groupName + "”") + "的聊天记录";
        }
        String peer = sources.stream()
                .flatMap(s -> java.util.stream.Stream.of(s.senderId()))
                .filter(id -> id != null && !id.equals(userId))
                .findFirst()
                .orElse(null);
        if (peer == null) {
            var record = conversationRepository.findByConversationIdAndUserId(sourceId, userId);
            peer = record == null ? null : record.getTargetId();
        }
        String me = displayName(userId, names);
        return peer == null ? me + "的聊天记录" : me + "和" + displayName(peer, names) + "的聊天记录";
    }

    private ChatMediaService.ResolvedMedia refreshed(Source source) {
        return chatMediaService.refreshForForward(source.messageType(), source.content(), source.ext());
    }

    private MessageResponse sendToFriend(String target, String conversationId, String userId, Payload payload,
                                         String clientMessageId) {
        PrivateMessage existing = privateMessageRepository.findBySenderIdAndClientMessageId(userId, clientMessageId);
        if (existing != null) {
            return messageService.convertPrivateMessageToResponse(existing);
        }
        Date now = new Date();
        PrivateMessage message = new PrivateMessage();
        message.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
        message.setSenderId(userId);
        message.setReceiverId(target);
        message.setConversationId(conversationId);
        message.setMessageType(payload.messageType());
        message.setContent(payload.content());
        message.setExt(payload.ext() == null ? null : new LinkedHashMap<>(payload.ext()));
        message.setForwardFrom(payload.forwardFrom());
        message.setContentRevision(1);
        message.setClientMessageId(clientMessageId);
        message.setStatus(0);
        message.setSeqId(IdUtil.getSnowflakeNextIdStr());
        message.setTimestamp(now);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        chatMediaService.attachToConversation(message.getContent(), conversationId);
        privateMessageRepository.save(message);
        conversationRecords.onPrivateMessage(message);
        redisStreamService.publishPrivateMessage("PRIVATE_MESSAGE", userId, serialize(message), target);
        return messageService.convertPrivateMessageToResponse(message);
    }

    private MessageResponse sendToGroup(String target, String userId, Payload payload, String clientMessageId) {
        GroupMessage existing = groupMessageRepository.findBySenderIdAndClientMessageId(userId, clientMessageId);
        if (existing != null) {
            return messageService.convertGroupMessageToResponse(existing);
        }
        Date now = new Date();
        GroupMessage message = new GroupMessage();
        message.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
        message.setGroupId(target);
        message.setSenderId(userId);
        message.setMessageType(payload.messageType());
        message.setContent(payload.content());
        message.setExt(payload.ext() == null ? null : new LinkedHashMap<>(payload.ext()));
        message.setForwardFrom(payload.forwardFrom());
        message.setContentRevision(1);
        message.setClientMessageId(clientMessageId);
        message.setStatus(0);
        message.setSeqId(IdUtil.getSnowflakeNextIdStr());
        message.setTimestamp(now);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        chatMediaService.attachToConversation(message.getContent(), target);
        List<String> memberIds = groupMemberIds(target);
        groupMessageRepository.save(message);
        conversationRecords.onGroupMessage(message, memberIds);
        redisStreamService.publishGroupMessage("GROUP_MESSAGE", userId, serialize(message), memberIds);
        return messageService.convertGroupMessageToResponse(message);
    }

    private List<String> groupMemberIds(String groupId) {
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

    private String displayName(String userId, Map<String, String> cache) {
        if (userId == null) {
            return "未知用户";
        }
        return cache.computeIfAbsent(userId, id -> {
            UserBriefResponse brief = brief(id);
            if (brief == null) {
                return id;
            }
            if (brief.getNickname() != null && !brief.getNickname().isBlank()) {
                return brief.getNickname();
            }
            return brief.getUsername() != null ? brief.getUsername() : id;
        });
    }

    private UserBriefResponse brief(String userId) {
        try {
            return userService.getUserBriefInfoByID(userId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String serialize(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("消息序列化失败", e);
        }
    }

    private static String formatTimestamp(Date date) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(date);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> distinct(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                result.add(trimmed);
            }
        }
        return new ArrayList<>(result);
    }
}
