package icu.tianqingyuluo.onlineim.config;

import icu.tianqingyuluo.onlineim.mapper.GroupMemberMapper;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.service.ConversationRecordService;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 启动时把旧的“每个会话一条共享文档”迁移成“每个参与者一条记录”（见 docs/conversation-model.md）。
 *
 * <p>幂等：只处理没有 conversationId 字段的旧文档，写入一律 setOnInsert，不会覆盖已存在的新记录；
 * 全量群成员补建只执行一次（schema_migrations 中记标记）。</p>
 */
@Slf4j
@Component
public class ConversationRecordMigration implements InitializingBean {

    static final String COLLECTION = "conversations";
    static final String MIGRATIONS = "schema_migrations";
    static final String BACKFILL_MARKER = "conversation-per-user-v1-group-backfill";
    static final String UNIQUE_INDEX = "uk_user_conversation";

    private final MongoTemplate mongoTemplate;
    private final GroupMemberMapper groupMemberMapper;
    private final ConversationRecordService conversationRecords;

    public ConversationRecordMigration(MongoTemplate mongoTemplate,
                                       GroupMemberMapper groupMemberMapper,
                                       ConversationRecordService conversationRecords) {
        this.mongoTemplate = mongoTemplate;
        this.groupMemberMapper = groupMemberMapper;
        this.conversationRecords = conversationRecords;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            int converted = migrateLegacyDocuments();
            ensureIndexes();
            int groups = backfillGroupMembersOnce();
            if (converted > 0 || groups > 0) {
                log.info("会话记录迁移完成: 旧文档转换 {} 条, 补建群会话的群 {} 个", converted, groups);
            }
        } catch (RuntimeException ex) {
            // 迁移失败不阻止服务启动；下次启动会继续处理剩余的旧文档
            log.error("会话记录迁移失败: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 旧文档：_id 即会话 ID、没有 conversationId 字段。按参与者拆成多条记录后删除旧文档。
     */
    int migrateLegacyDocuments() {
        Query legacy = Query.query(Criteria.where("conversationId").exists(false));
        List<Document> docs = mongoTemplate.find(legacy, Document.class, COLLECTION);
        int converted = 0;
        for (Document doc : docs) {
            Object rawId = doc.get("_id");
            if (!(rawId instanceof String conversationId)) {
                continue;
            }
            String ownerId = doc.getString("userId");
            String targetId = doc.getString("targetId");
            BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, COLLECTION);
            if (conversationId.startsWith("grp_")) {
                List<String> members = groupMemberMapper.listUserIdsByGroupId(conversationId);
                if (members == null || members.isEmpty()) {
                    // 群已解散：旧文档没有意义，直接清理
                    mongoTemplate.remove(Query.query(Criteria.where("_id").is(conversationId)), COLLECTION);
                    converted++;
                    continue;
                }
                for (String member : members) {
                    // 旧文档只有创建者能看到，置顶/免打扰也只属于创建者；其他成员用默认值
                    boolean creator = member.equals(ownerId);
                    bulk.upsert(byId(conversationId, member), copy(doc, conversationId, "group",
                            member, conversationId, creator));
                }
            } else if (conversationId.startsWith("conv_") && ownerId != null && targetId != null
                    && !ownerId.equals(targetId)) {
                // 旧私聊文档被双方共享，双方看到的置顶/免打扰/删除状态一致，迁移后各自保留一份
                bulk.upsert(byId(conversationId, ownerId), copy(doc, conversationId, "private", ownerId, targetId, true));
                bulk.upsert(byId(conversationId, targetId), copy(doc, conversationId, "private", targetId, ownerId, true));
            } else {
                log.warn("跳过无法识别的旧会话文档: _id={}", conversationId);
                continue;
            }
            bulk.execute();
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(conversationId)), COLLECTION);
            converted++;
        }
        return converted;
    }

    void ensureIndexes() {
        var indexOps = mongoTemplate.indexOps(COLLECTION);
        indexOps.ensureIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .on("conversationId", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("conversationId").exists(true)))
                .named(UNIQUE_INDEX));
        indexOps.ensureIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC)
                .on("top", Sort.Direction.DESC)
                .on("updatedAt", Sort.Direction.DESC)
                .named("idx_user_status_top_updated"));
        indexOps.ensureIndex(new Index()
                .on("conversationId", Sort.Direction.ASC)
                .named("idx_conversation_id"));
    }

    /**
     * 旧模型下群会话只有点开过的成员才有文档；一次性为所有现存群的所有成员补建（只插入缺失的）。
     */
    int backfillGroupMembersOnce() {
        if (mongoTemplate.exists(Query.query(Criteria.where("_id").is(BACKFILL_MARKER)), MIGRATIONS)) {
            return 0;
        }
        List<String> groupIds = groupMemberMapper.listGroupIdsWithMembers();
        int count = 0;
        for (String groupId : groupIds == null ? List.<String>of() : groupIds) {
            List<String> members = groupMemberMapper.listUserIdsByGroupId(groupId);
            if (members != null && !members.isEmpty()) {
                conversationRecords.ensureGroupMembers(groupId, members);
                count++;
            }
        }
        mongoTemplate.save(new Document(Map.of("_id", BACKFILL_MARKER, "appliedAt", new Date(), "groups", count)),
                MIGRATIONS);
        return count;
    }

    private Query byId(String conversationId, String userId) {
        return Query.query(Criteria.where("_id").is(ConversationRecordService.recordId(conversationId, userId)));
    }

    private Update copy(Document legacy, String conversationId, String type, String ownerId, String targetId,
                        boolean keepPreferences) {
        Object updatedAt = legacy.get("updatedAt");
        Object lastMessage = legacy.get("lastMessage");
        Integer status = legacy.getInteger("status");
        return new Update()
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("userId", ownerId)
                .setOnInsert("targetId", targetId)
                .setOnInsert("conversationType", type)
                .setOnInsert("unreadCount", 0)
                .setOnInsert("lastMessage", lastMessage == null ? new Document() : lastMessage)
                .setOnInsert("top", keepPreferences && Boolean.TRUE.equals(legacy.getBoolean("top")))
                .setOnInsert("mute", keepPreferences && Boolean.TRUE.equals(legacy.getBoolean("mute")))
                .setOnInsert("status", keepPreferences && status != null ? status : 1)
                .setOnInsert("updatedAt", updatedAt == null ? new Date() : updatedAt)
                .setOnInsert("_class", Conversation.class.getName());
    }
}
