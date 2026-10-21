package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.ConversationReadState;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import icu.tianqingyuluo.onlineim.util.SeqIdComparator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 每个参与者一条会话记录的维护入口（见 docs/conversation-model.md）。
 *
 * <p>所有写入都按确定性的 {@code _id = conversationId:userId} 做 upsert，并发重复调用也只会得到一条记录；
 * 只写调用方/参与者自己的那一条，不会覆盖其他人的置顶、免打扰、删除状态。</p>
 *
 * <p>只依赖 MongoTemplate，避免与 GroupService / ConversationService 之间形成循环依赖。</p>
 */
@Slf4j
@Service
public class ConversationRecordService {

    public static final String TYPE_PRIVATE = "private";
    public static final String TYPE_GROUP = "group";
    static final int PREVIEW_MAX_LENGTH = 100;
    /** 每条会话记录最多保留的未读 @我 条数 */
    static final int MAX_PENDING_MENTIONS = 20;

    private final MongoTemplate mongoTemplate;

    public ConversationRecordService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public static String recordId(String conversationId, String userId) {
        return conversationId + ":" + userId;
    }

    /**
     * 确保 owner 在会话上有自己的记录。已存在时保留置顶/免打扰/未读/最后消息；
     * {@code visible=true} 时把被删除（隐藏）的记录恢复显示，{@code false} 时仅在缺失时以隐藏状态创建。
     */
    public Conversation ensureRecord(String conversationId, String conversationType,
                                     String ownerId, String targetId, boolean visible) {
        Update update = insertDefaults(conversationId, conversationType, ownerId, targetId, !visible);
        if (visible) {
            update.set("status", 1);
        }
        return mongoTemplate.findAndModify(byRecordId(conversationId, ownerId), update,
                FindAndModifyOptions.options().upsert(true).returnNew(true), Conversation.class);
    }

    /**
     * 群成员加入：为每个成员创建（或恢复显示）群会话记录，不改动已有的置顶/免打扰。
     */
    public void ensureGroupMembers(String groupId, Collection<String> userIds) {
        Set<String> ids = distinct(userIds);
        if (groupId == null || ids.isEmpty()) {
            return;
        }
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Conversation.class);
        for (String userId : ids) {
            bulk.upsert(byRecordId(groupId, userId),
                    insertDefaults(groupId, TYPE_GROUP, userId, groupId, false).set("status", 1));
        }
        bulk.execute();
    }

    /**
     * 退群/被移出：删除该成员的群会话记录（消息与已读游标保留）。
     */
    public void removeRecord(String conversationId, String userId) {
        if (conversationId == null || userId == null) {
            return;
        }
        mongoTemplate.remove(byRecordId(conversationId, userId), Conversation.class);
    }

    /**
     * 解散群：删除所有成员的群会话记录。
     */
    public void removeConversation(String conversationId) {
        if (conversationId == null) {
            return;
        }
        mongoTemplate.remove(Query.query(Criteria.where("conversationId").is(conversationId)), Conversation.class);
    }

    /**
     * 只修改 owner 自己那条记录，返回是否找到记录。
     */
    public boolean updateOwn(String conversationId, String ownerId, Update update) {
        return mongoTemplate.updateFirst(byRecordId(conversationId, ownerId), update, Conversation.class)
                .getMatchedCount() > 0;
    }

    /**
     * 把会话对所有参与者隐藏（删除好友时使用），置顶/免打扰保留，新消息或重新打开会恢复显示。
     */
    public void hideForAll(String conversationId) {
        mongoTemplate.updateMulti(Query.query(Criteria.where("conversationId").is(conversationId)),
                new Update().set("status", 0).set("updatedAt", new Date()), Conversation.class);
    }

    /**
     * 新私聊消息：双方记录的 lastMessage/updatedAt 更新并恢复显示，只给接收方未读 +1。
     */
    public void onPrivateMessage(PrivateMessage message) {
        if (message == null || message.getConversationId() == null) {
            return;
        }
        try {
            Conversation.LastMessage last = lastMessage(message.getId(), message.getMessageType(),
                    message.getContent(), message.getTimestamp(), message.getSenderId());
            BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Conversation.class);
            bulk.upsert(byRecordId(message.getConversationId(), message.getSenderId()),
                    newMessageUpdate(message.getConversationId(), TYPE_PRIVATE, message.getSenderId(),
                            message.getReceiverId(), last, false));
            bulk.upsert(byRecordId(message.getConversationId(), message.getReceiverId()),
                    newMessageUpdate(message.getConversationId(), TYPE_PRIVATE, message.getReceiverId(),
                            message.getSenderId(), last, true));
            bulk.execute();
        } catch (RuntimeException ex) {
            log.warn("更新私聊会话记录失败: conversationId={}, error={}", message.getConversationId(), ex.getMessage());
        }
    }

    /**
     * 新群消息：所有当前成员的记录更新 lastMessage 并恢复显示，除发送者外未读 +1。
     */
    public void onGroupMessage(GroupMessage message, Collection<String> memberIds) {
        if (message == null || message.getGroupId() == null) {
            return;
        }
        Set<String> members = distinct(memberIds);
        if (message.getSenderId() != null) {
            members.add(message.getSenderId());
        }
        try {
            Conversation.LastMessage last = lastMessage(message.getId(), message.getMessageType(),
                    message.getContent(), message.getTimestamp(), message.getSenderId());
            BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Conversation.class);
            Set<String> mentioned = distinct(message.getAtUsers());
            for (String userId : members) {
                Update update = newMessageUpdate(message.getGroupId(), TYPE_GROUP, userId, message.getGroupId(), last,
                        !userId.equals(message.getSenderId()));
                if (mentioned.contains(userId) && !userId.equals(message.getSenderId())) {
                    // 被 @ 的成员记一条“有人@我”，读到该消息（或撤回）时移除
                    update.push("pendingMentions").slice(-MAX_PENDING_MENTIONS).each(
                            Conversation.PendingMention.builder()
                                    .messageId(message.getId())
                                    .seqId(message.getSeqId())
                                    .senderId(message.getSenderId())
                                    .build());
                }
                bulk.upsert(byRecordId(message.getGroupId(), userId), update);
            }
            bulk.execute();
        } catch (RuntimeException ex) {
            log.warn("更新群会话记录失败: groupId={}, error={}", message.getGroupId(), ex.getMessage());
        }
    }

    /**
     * 已读推进后把读者自己的未读缓存同步为权威计算值。
     */
    public void syncUnread(String conversationId, String userId, long unreadCount) {
        try {
            updateOwn(conversationId, userId, new Update().set("unreadCount", Math.toIntExact(Math.max(0, unreadCount))));
        } catch (RuntimeException ex) {
            log.warn("同步未读缓存失败: conversationId={}, userId={}, error={}", conversationId, userId, ex.getMessage());
        }
    }

    /**
     * 已读推进后移除读者自己记录上 seqId ≤ readSeq 的 @我。
     */
    public void pruneMentions(String conversationId, String userId, String readSeq) {
        if (conversationId == null || userId == null || readSeq == null) {
            return;
        }
        try {
            Conversation record = mongoTemplate.findOne(byRecordId(conversationId, userId), Conversation.class);
            if (record == null || record.getPendingMentions() == null || record.getPendingMentions().isEmpty()) {
                return;
            }
            List<Conversation.PendingMention> remaining = unreadMentions(record.getPendingMentions(), readSeq);
            if (remaining.size() != record.getPendingMentions().size()) {
                updateOwn(conversationId, userId, new Update().set("pendingMentions", remaining));
            }
        } catch (RuntimeException ex) {
            log.warn("清理 @我 标记失败: conversationId={}, userId={}, error={}", conversationId, userId, ex.getMessage());
        }
    }

    /** 过滤出 seqId 大于 readSeq 的 @我（readSeq 为空时全部保留） */
    public static List<Conversation.PendingMention> unreadMentions(List<Conversation.PendingMention> mentions, String readSeq) {
        if (mentions == null || mentions.isEmpty()) {
            return List.of();
        }
        List<Conversation.PendingMention> result = new ArrayList<>();
        for (Conversation.PendingMention mention : mentions) {
            if (mention == null || mention.getSeqId() == null) {
                continue;
            }
            if (readSeq == null || readSeq.isBlank() || SeqIdComparator.compare(mention.getSeqId(), readSeq) > 0) {
                result.add(mention);
            }
        }
        return result;
    }

    /**
     * 消息被撤回：除 {@link #onMessageRecalled(String, String)} 外，还把这条消息从还没读到它的接收者的未读缓存里减掉
     * （读游标 &lt; 该消息 seq 的成员，发送者本人除外）。权威未读数由读游标实时计算，并已排除撤回的消息。
     */
    public void onMessageRecalled(String conversationId, String messageId, String seqId, String senderId) {
        onMessageRecalled(conversationId, messageId);
        if (conversationId == null || seqId == null || seqId.isBlank()) {
            return;
        }
        try {
            Query unread = Query.query(Criteria.where("conversationId").is(conversationId)
                    .and("userId").ne(senderId)
                    .and("unreadCount").gt(0));
            unread.fields().include("_id").include("userId");
            for (Conversation record : mongoTemplate.find(unread, Conversation.class)) {
                if (record.getUserId() == null || !notYetRead(conversationId, record.getUserId(), seqId)) {
                    continue;
                }
                mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(record.getId()).and("unreadCount").gt(0)),
                        new Update().inc("unreadCount", -1), Conversation.class);
            }
        } catch (RuntimeException ex) {
            log.warn("撤回后扣减未读缓存失败: conversationId={}, error={}", conversationId, ex.getMessage());
        }
    }

    /** 该成员的读游标是否还没到 seqId（没有读状态视为未读） */
    private boolean notYetRead(String conversationId, String userId, String seqId) {
        ConversationReadState state = mongoTemplate.findById(recordId(conversationId, userId), ConversationReadState.class);
        String readSeq = state == null ? null : state.getReadSeq();
        if (readSeq == null || readSeq.isBlank()) {
            return true;
        }
        try {
            return SeqIdComparator.compare(seqId, readSeq) > 0;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /**
     * 消息被撤回：如果它是各参与者记录里的最后一条消息，把预览替换掉，避免列表继续展示原文。
     * 同时移除各成员记录上指向它的 @我。
     */
    public void onMessageRecalled(String conversationId, String messageId) {
        try {
            mongoTemplate.updateMulti(Query.query(Criteria.where("conversationId").is(conversationId)
                            .and("pendingMentions.messageId").is(messageId)),
                    new Update().pull("pendingMentions", Query.query(Criteria.where("messageId").is(messageId)).getQueryObject()),
                    Conversation.class);
        } catch (RuntimeException ex) {
            log.warn("移除撤回消息的 @我 失败: conversationId={}, error={}", conversationId, ex.getMessage());
        }
        try {
            mongoTemplate.updateMulti(Query.query(Criteria.where("conversationId").is(conversationId)
                            .and("lastMessage.messageId").is(messageId)),
                    new Update().set("lastMessage.content", "[消息已撤回]").set("lastMessage.messageType", "recalled"),
                    Conversation.class);
        } catch (RuntimeException ex) {
            log.warn("更新撤回预览失败: conversationId={}, error={}", conversationId, ex.getMessage());
        }
    }

    /**
     * 消息被编辑：如果它是各参与者记录里的最后一条消息，把预览换成新内容（不改 updatedAt / 未读数，列表不重新排序）。
     */
    public void onMessageEdited(String conversationId, String messageId, String messageType, String content) {
        try {
            mongoTemplate.updateMulti(Query.query(Criteria.where("conversationId").is(conversationId)
                            .and("lastMessage.messageId").is(messageId)),
                    new Update().set("lastMessage.content", preview(messageType, content)),
                    Conversation.class);
        } catch (RuntimeException ex) {
            log.warn("更新编辑预览失败: conversationId={}, error={}", conversationId, ex.getMessage());
        }
    }

    /**
     * 群消息编辑后 @ 列表变化：不再被 @ 的成员移除这条“有人@我”，新被 @ 的成员补一条（已读过的会在下次已读推进时清掉）。
     */
    public void onMentionsEdited(GroupMessage message, Collection<String> removed, Collection<String> added) {
        if (message == null || message.getGroupId() == null) {
            return;
        }
        for (String userId : distinct(removed)) {
            try {
                updateOwn(message.getGroupId(), userId, new Update().pull("pendingMentions",
                        Query.query(Criteria.where("messageId").is(message.getId())).getQueryObject()));
            } catch (RuntimeException ex) {
                log.warn("移除编辑后失效的 @我 失败: groupId={}, userId={}, error={}", message.getGroupId(), userId, ex.getMessage());
            }
        }
        for (String userId : distinct(added)) {
            if (userId.equals(message.getSenderId())) {
                continue;
            }
            try {
                mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(recordId(message.getGroupId(), userId))
                                .and("pendingMentions.messageId").ne(message.getId())),
                        new Update().push("pendingMentions").slice(-MAX_PENDING_MENTIONS).each(
                                Conversation.PendingMention.builder()
                                        .messageId(message.getId())
                                        .seqId(message.getSeqId())
                                        .senderId(message.getSenderId())
                                        .build()),
                        Conversation.class);
            } catch (RuntimeException ex) {
                log.warn("补充编辑后的 @我 失败: groupId={}, userId={}, error={}", message.getGroupId(), userId, ex.getMessage());
            }
        }
    }

    static String preview(String messageType, String content) {
        String type = messageType == null ? "" : messageType.toLowerCase();
        return switch (type) {
            case "text", "" -> {
                String text = content == null ? "" : content;
                yield text.length() > PREVIEW_MAX_LENGTH ? text.substring(0, PREVIEW_MAX_LENGTH) : text;
            }
            case "image" -> "[图片]";
            case "file" -> "[文件]";
            case "voice", "audio" -> "[语音]";
            case "video" -> "[视频]";
            case "emoji", "sticker" -> "[表情]";
            case "chat_record" -> "[聊天记录]";
            default -> "[消息]";
        };
    }

    private Update newMessageUpdate(String conversationId, String type, String ownerId, String targetId,
                                    Conversation.LastMessage last, boolean incrementUnread) {
        Update update = new Update()
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("userId", ownerId)
                .setOnInsert("targetId", targetId)
                .setOnInsert("conversationType", type)
                .setOnInsert("top", false)
                .setOnInsert("mute", false)
                .setOnInsert("_class", Conversation.class.getName())
                .set("lastMessage", last)
                .set("status", 1)
                .set("updatedAt", last.getTimestamp());
        if (incrementUnread) {
            update.inc("unreadCount", 1);
        } else {
            update.setOnInsert("unreadCount", 0);
        }
        return update;
    }

    private Update insertDefaults(String conversationId, String type, String ownerId, String targetId, boolean hidden) {
        Update update = new Update()
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("userId", ownerId)
                .setOnInsert("targetId", targetId)
                .setOnInsert("conversationType", type)
                .setOnInsert("unreadCount", 0)
                .setOnInsert("lastMessage", new Conversation.LastMessage())
                .setOnInsert("top", false)
                .setOnInsert("mute", false)
                .setOnInsert("updatedAt", new Date())
                .setOnInsert("_class", Conversation.class.getName());
        if (hidden) {
            update.setOnInsert("status", 0);
        }
        return update;
    }

    private Conversation.LastMessage lastMessage(String messageId, String messageType, String content, Date timestamp,
                                                 String senderId) {
        return Conversation.LastMessage.builder()
                .messageId(messageId)
                .senderId(senderId)
                .messageType(messageType)
                .content(preview(messageType, content))
                .timestamp(timestamp == null ? new Date() : timestamp)
                .build();
    }

    private Query byRecordId(String conversationId, String userId) {
        return Query.query(Criteria.where("_id").is(recordId(conversationId, userId)));
    }

    private Set<String> distinct(Collection<String> ids) {
        Set<String> result = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(Objects::nonNull).filter(id -> !id.isBlank()).forEach(result::add);
        }
        return result;
    }
}
