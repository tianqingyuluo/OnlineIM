package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.SyncRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ConversationResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.OfflineSyncResponse;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.util.SeqIdComparator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket 连接或重连后，按客户端上报的各会话游标补齐漏收消息、未读数和最新预览。
 *
 * <p>不另建离线收件箱：消息仍在私聊 / 群聊集合里，按 seq 增量读取。单次推送有上限，
 * 超出时 {@code truncated=true}，由客户端对正在查看的会话再走既有 HTTP 增量接口。</p>
 */
@Service
public class OfflineSyncService {

    public static final int MESSAGE_LIMIT_PER_CONVERSATION = 50;
    public static final int MESSAGE_LIMIT_TOTAL = 200;
    public static final int MAX_CURSORS = 100;
    public static final int MAX_WATCH_IDS = 100;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final PrivateMessageRepository privateMessageRepository;
    private final GroupMessageRepository groupMessageRepository;

    public OfflineSyncService(ConversationService conversationService,
                              MessageService messageService,
                              PrivateMessageRepository privateMessageRepository,
                              GroupMessageRepository groupMessageRepository) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
    }

    public OfflineSyncResponse sync(String userId, SyncRequest request) {
        OfflineSyncResponse response = new OfflineSyncResponse();
        if (userId == null || userId.isBlank()) {
            return response;
        }
        List<ConversationResponse> conversations = conversationService.getUserConversations(userId);
        response.setConversations(conversations == null ? List.of() : conversations);
        if (request == null) {
            return response;
        }

        Map<String, ConversationResponse> visible = new LinkedHashMap<>();
        if (conversations != null) {
            for (ConversationResponse conversation : conversations) {
                if (conversation != null && conversation.getConversationID() != null) {
                    visible.put(conversation.getConversationID(), conversation);
                }
            }
        }

        Set<String> pushedIds = new LinkedHashSet<>();
        int budget = MESSAGE_LIMIT_TOTAL;
        List<SyncRequest.ConversationCursor> cursors = request.getConversations() == null
                ? List.of() : request.getConversations();
        int cursorCount = 0;
        for (SyncRequest.ConversationCursor cursor : cursors) {
            if (cursor == null || cursor.getConversationId() == null || cursor.getConversationId().isBlank()) {
                continue;
            }
            if (cursorCount >= MAX_CURSORS) {
                response.setTruncated(true);
                break;
            }
            cursorCount++;
            if (!visible.containsKey(cursor.getConversationId())) {
                continue;
            }
            String lastSeq = normalizeSeq(cursor.getLastSeq());
            if (lastSeq == null) {
                continue;
            }
            boolean group = cursor.getConversationId().startsWith("grp_");
            // 仓库按 seq 降序给出最新的若干条；先截断再翻成升序，保证留下的是最新而不是最旧的一段
            List<MessageResponse> newestFirst = group
                    ? missedGroupMessages(cursor.getConversationId(), lastSeq)
                    : missedPrivateMessages(cursor.getConversationId(), lastSeq);
            if (newestFirst.size() > MESSAGE_LIMIT_PER_CONVERSATION) {
                response.setTruncated(true);
                newestFirst = newestFirst.subList(0, MESSAGE_LIMIT_PER_CONVERSATION);
            }
            if (budget <= 0) {
                if (!newestFirst.isEmpty()) {
                    response.setTruncated(true);
                }
            } else if (!newestFirst.isEmpty()) {
                if (newestFirst.size() > budget) {
                    response.setTruncated(true);
                    newestFirst = newestFirst.subList(0, budget);
                }
                List<MessageResponse> ascending = new ArrayList<>(newestFirst);
                java.util.Collections.reverse(ascending);
                for (MessageResponse message : ascending) {
                    if (message == null || message.getMessageId() == null || !pushedIds.add(message.getMessageId())) {
                        continue;
                    }
                    response.getMessages().add(message);
                    budget--;
                }
            }
            appendRevisions(response, cursor.getConversationId(), lastSeq, group, pushedIds);
        }

        appendWatchedRevisions(response, request.getWatch(), visible.keySet(), pushedIds);
        return response;
    }

    /** 按 seq 降序（最新在前）。调用方截断后再翻成升序。 */
    private List<MessageResponse> missedPrivateMessages(String conversationId, String lastSeq) {
        List<PrivateMessage> newest = privateMessageRepository.findNewestAfterSeqId(conversationId, lastSeq);
        if (newest == null || newest.isEmpty()) {
            return List.of();
        }
        List<MessageResponse> converted = new ArrayList<>(newest.size());
        for (PrivateMessage message : newest) {
            if (message != null) {
                converted.add(messageService.convertPrivateMessageToResponse(message));
            }
        }
        return converted;
    }

    /** 按 seq 降序（最新在前）。调用方截断后再翻成升序。 */
    private List<MessageResponse> missedGroupMessages(String conversationId, String lastSeq) {
        List<GroupMessage> newest = groupMessageRepository.findNewestAfterSeqId(conversationId, lastSeq);
        if (newest == null || newest.isEmpty()) {
            return List.of();
        }
        List<MessageResponse> converted = new ArrayList<>(newest.size());
        for (GroupMessage message : newest) {
            if (message != null) {
                converted.add(messageService.convertGroupMessageToResponse(message));
            }
        }
        return converted;
    }

    private void appendRevisions(OfflineSyncResponse response, String conversationId, String lastSeq,
                                 boolean group, Set<String> pushedIds) {
        if ("0".equals(lastSeq)) {
            return;
        }
        if (group) {
            List<GroupMessage> revisions = groupMessageRepository.findRevisionsAtOrBeforeSeq(conversationId, lastSeq);
            if (revisions == null) {
                return;
            }
            for (GroupMessage message : revisions) {
                addRevision(response, pushedIds, message == null ? null : message.getId(),
                        message != null && revised(message.getStatus(), message.getEditedAt()),
                        () -> messageService.convertGroupMessageToResponse(message));
            }
        } else {
            List<PrivateMessage> revisions = privateMessageRepository.findRevisionsAtOrBeforeSeq(conversationId, lastSeq);
            if (revisions == null) {
                return;
            }
            for (PrivateMessage message : revisions) {
                addRevision(response, pushedIds, message == null ? null : message.getId(),
                        message != null && revised(message.getStatus(), message.getEditedAt()),
                        () -> messageService.convertPrivateMessageToResponse(message));
            }
        }
    }

    private void appendWatchedRevisions(OfflineSyncResponse response, List<SyncRequest.WatchCursor> watches,
                                        Set<String> visibleIds, Set<String> pushedIds) {
        if (watches == null) {
            return;
        }
        for (SyncRequest.WatchCursor watch : watches) {
            if (watch == null || watch.getConversationId() == null || !visibleIds.contains(watch.getConversationId())) {
                continue;
            }
            List<String> ids = limitIds(watch.getMessageIds());
            if (ids.isEmpty()) {
                continue;
            }
            if (watch.getConversationId().startsWith("grp_")) {
                List<GroupMessage> messages = groupMessageRepository.findByGroupIdAndIdIn(watch.getConversationId(), ids);
                if (messages == null) {
                    continue;
                }
                for (GroupMessage message : messages) {
                    addRevision(response, pushedIds, message == null ? null : message.getId(),
                            message != null && revised(message.getStatus(), message.getEditedAt()),
                            () -> messageService.convertGroupMessageToResponse(message));
                }
            } else {
                List<PrivateMessage> messages = privateMessageRepository.findByConversationIdAndIdIn(
                        watch.getConversationId(), ids);
                if (messages == null) {
                    continue;
                }
                for (PrivateMessage message : messages) {
                    addRevision(response, pushedIds, message == null ? null : message.getId(),
                            message != null && revised(message.getStatus(), message.getEditedAt()),
                            () -> messageService.convertPrivateMessageToResponse(message));
                }
            }
        }
    }

    private void addRevision(OfflineSyncResponse response, Set<String> pushedIds, String messageId,
                             boolean revised, RevisionLoader loader) {
        if (!revised || messageId == null || !pushedIds.add(messageId)) {
            return;
        }
        MessageResponse converted = loader.load();
        if (converted != null) {
            response.getRevisions().add(converted);
        }
    }

    private static boolean revised(Integer status, java.util.Date editedAt) {
        return (status != null && status == 3) || editedAt != null;
    }

    private static List<String> limitIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> limited = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            limited.add(id);
            if (limited.size() >= MAX_WATCH_IDS) {
                break;
            }
        }
        return limited;
    }

    private static String normalizeSeq(String seqId) {
        if (seqId == null || seqId.isBlank()) {
            return "0";
        }
        try {
            return SeqIdComparator.requireValid(seqId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @FunctionalInterface
    private interface RevisionLoader {
        MessageResponse load();
    }
}
