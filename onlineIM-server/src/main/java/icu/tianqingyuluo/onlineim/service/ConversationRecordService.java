package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
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

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
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
                    message.getContent(), message.getTimestamp());
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
                    message.getContent(), message.getTimestamp());
            BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Conversation.class);
            for (String userId : members) {
                bulk.upsert(byRecordId(message.getGroupId(), userId),
                        newMessageUpdate(message.getGroupId(), TYPE_GROUP, userId, message.getGroupId(), last,
                                !userId.equals(message.getSenderId())));
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
     * 消息被撤回：如果它是各参与者记录里的最后一条消息，把预览替换掉，避免列表继续展示原文。
     */
    public void onMessageRecalled(String conversationId, String messageId) {
        try {
            mongoTemplate.updateMulti(Query.query(Criteria.where("conversationId").is(conversationId)
                            .and("lastMessage.messageId").is(messageId)),
                    new Update().set("lastMessage.content", "[消息已撤回]").set("lastMessage.messageType", "recalled"),
                    Conversation.class);
        } catch (RuntimeException ex) {
            log.warn("更新撤回预览失败: conversationId={}, error={}", conversationId, ex.getMessage());
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

    private Conversation.LastMessage lastMessage(String messageId, String messageType, String content, Date timestamp) {
        return Conversation.LastMessage.builder()
                .messageId(messageId)
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
