package icu.tianqingyuluo.onlineim.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import icu.tianqingyuluo.onlineim.config.ConversationRecordMigration;
import icu.tianqingyuluo.onlineim.mapper.GroupMemberMapper;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 集成测试：用真实 MongoDB 验证“每个参与者一条会话记录”的写入语义与启动迁移。
 */
@Testcontainers
class ConversationRecordServiceIntegrationTest {

    private static final String A = "usr_a";
    private static final String B = "usr_b";
    private static final String C = "usr_c";
    private static final String PRIV = "conv_usr_ausr_b";
    private static final String GRP = "grp_1";

    private static GenericContainer<?> mongo;
    private static MongoClient client;
    private static MongoTemplate template;
    private ConversationRecordService records;

    @BeforeAll
    static void startMongo() {
        mongo = new GenericContainer<>(DockerImageName.parse("mongo:7")).withExposedPorts(27017);
        mongo.start();
        client = MongoClients.create("mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017));
        template = new MongoTemplate(client, "conv_record_test");
    }

    @AfterAll
    static void stopMongo() {
        if (client != null) {
            client.close();
        }
        if (mongo != null) {
            mongo.stop();
        }
    }

    @BeforeEach
    void clean() {
        template.getDb().drop();
        records = new ConversationRecordService(template);
    }

    private Conversation record(String conversationId, String userId) {
        return template.findById(ConversationRecordService.recordId(conversationId, userId), Conversation.class);
    }

    @Test
    void ensureRecordIsIdempotentAndKeepsOwnerPreferences() {
        records.ensureRecord(PRIV, "private", A, B, true);
        records.updateOwn(PRIV, A, new Update().set("top", true).set("mute", true));
        records.ensureRecord(PRIV, "private", A, B, true);

        Conversation a = record(PRIV, A);
        assertEquals(PRIV, a.getConversationId());
        assertEquals(B, a.getTargetId());
        assertTrue(a.getTop());
        assertTrue(a.getMute());
        assertEquals(1, a.getStatus());
        assertEquals(1, template.count(new Query(), Conversation.class));
    }

    @Test
    void peerRecordIsCreatedHiddenAndExistingPeerStateIsUntouched() {
        records.ensureRecord(PRIV, "private", B, A, false);
        assertEquals(0, record(PRIV, B).getStatus());

        records.ensureRecord(PRIV, "private", B, A, true);
        records.updateOwn(PRIV, B, new Update().set("top", true));
        records.ensureRecord(PRIV, "private", B, A, false);
        Conversation b = record(PRIV, B);
        assertEquals(1, b.getStatus());
        assertTrue(b.getTop());
    }

    @Test
    void deleteHidesOnlyOwnRecordAndNewMessageRevivesItWithPreferences() {
        records.ensureRecord(PRIV, "private", A, B, true);
        records.ensureRecord(PRIV, "private", B, A, true);
        records.updateOwn(PRIV, B, new Update().set("top", true).set("mute", true));
        records.updateOwn(PRIV, B, new Update().set("status", 0));
        assertEquals(1, record(PRIV, A).getStatus());
        assertEquals(0, record(PRIV, B).getStatus());

        for (int i = 0; i < 3; i++) {
            records.onPrivateMessage(privateMessage("msg_" + i, A, B, "hi " + i));
        }

        Conversation a = record(PRIV, A);
        Conversation b = record(PRIV, B);
        assertEquals(0, a.getUnreadCount());
        assertEquals(3, b.getUnreadCount());
        assertEquals("msg_2", a.getLastMessage().getMessageId());
        assertEquals("msg_2", b.getLastMessage().getMessageId());
        assertEquals("hi 2", b.getLastMessage().getContent());
        assertEquals(1, b.getStatus());
        assertTrue(b.getTop());
        assertTrue(b.getMute());
        assertFalse(a.getTop());
    }

    @Test
    void groupMessageIncrementsEveryMemberExceptSender() {
        records.ensureGroupMembers(GRP, List.of(A, B, C));
        records.updateOwn(GRP, B, new Update().set("top", true));
        GroupMessage message = new GroupMessage();
        message.setId("msg_g");
        message.setGroupId(GRP);
        message.setSenderId(A);
        message.setMessageType("image");
        message.setContent("https://x/y.png");
        message.setTimestamp(new Date());

        records.onGroupMessage(message, List.of(A, B, C));
        records.onGroupMessage(message, List.of(A, B, C));

        assertEquals(0, record(GRP, A).getUnreadCount());
        assertEquals(2, record(GRP, B).getUnreadCount());
        assertEquals(2, record(GRP, C).getUnreadCount());
        assertEquals("[图片]", record(GRP, C).getLastMessage().getContent());
        assertTrue(record(GRP, B).getTop());
        assertFalse(record(GRP, C).getTop());
    }

    @Test
    void readSyncTouchesOnlyTheReader() {
        records.onPrivateMessage(privateMessage("msg_1", A, B, "x"));
        records.onPrivateMessage(privateMessage("msg_2", B, A, "y"));
        records.syncUnread(PRIV, B, 0);
        assertEquals(0, record(PRIV, B).getUnreadCount());
        assertEquals(1, record(PRIV, A).getUnreadCount());
    }

    @Test
    void membershipRemovalAndFriendDeletion() {
        records.ensureGroupMembers(GRP, List.of(A, B, C));
        records.removeRecord(GRP, C);
        assertNull(record(GRP, C));
        assertNotNull(record(GRP, A));
        records.removeConversation(GRP);
        assertEquals(0, template.count(Query.query(Criteria.where("conversationId").is(GRP)), Conversation.class));

        records.ensureRecord(PRIV, "private", A, B, true);
        records.ensureRecord(PRIV, "private", B, A, true);
        records.updateOwn(PRIV, A, new Update().set("top", true));
        records.hideForAll(PRIV);
        assertEquals(0, record(PRIV, A).getStatus());
        assertEquals(0, record(PRIV, B).getStatus());
        assertTrue(record(PRIV, A).getTop());
    }

    @Test
    void recallReplacesLastMessagePreview() {
        records.onPrivateMessage(privateMessage("msg_secret", A, B, "secret"));
        records.onMessageRecalled(PRIV, "msg_secret");
        assertEquals("[消息已撤回]", record(PRIV, A).getLastMessage().getContent());
        assertEquals("[消息已撤回]", record(PRIV, B).getLastMessage().getContent());
    }

    @Test
    void concurrentUpsertsProduceOneRecordPerParticipant() {
        CompletableFuture<?>[] futures = IntStream.range(0, 20)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    records.onPrivateMessage(privateMessage("msg_c" + i, A, B, "c" + i));
                    records.ensureRecord(PRIV, "private", A, B, true);
                }))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
        assertEquals(2, template.count(Query.query(Criteria.where("conversationId").is(PRIV)), Conversation.class));
        assertEquals(20, record(PRIV, B).getUnreadCount());
    }

    @Test
    void migrationSplitsLegacyDocumentsAndIsIdempotent() {
        template.insert(new Document("_id", PRIV).append("userId", B).append("targetId", A)
                .append("conversationType", "private").append("unreadCount", 5).append("lastMessage", new Document())
                .append("top", true).append("mute", false).append("status", 1).append("updatedAt", new Date()), "conversations");
        template.insert(new Document("_id", GRP).append("userId", A).append("targetId", GRP)
                .append("conversationType", "group").append("unreadCount", 0).append("lastMessage", new Document())
                .append("top", false).append("mute", true).append("status", 1).append("updatedAt", new Date()), "conversations");
        template.insert(new Document("_id", "grp_gone").append("userId", A).append("targetId", "grp_gone")
                .append("conversationType", "group").append("status", 1), "conversations");
        GroupMemberMapper mapper = mock(GroupMemberMapper.class);
        when(mapper.listUserIdsByGroupId(GRP)).thenReturn(List.of(A, B, C));
        when(mapper.listUserIdsByGroupId("grp_2")).thenReturn(List.of(B, C));
        when(mapper.listUserIdsByGroupId("grp_gone")).thenReturn(List.of());
        when(mapper.listGroupIdsWithMembers()).thenReturn(List.of(GRP, "grp_2"));
        ConversationRecordMigration migration = new ConversationRecordMigration(template, mapper, records);

        migration.afterPropertiesSet();
        records.updateOwn(PRIV, A, new Update().set("top", false));
        migration.afterPropertiesSet();

        assertFalse(template.exists(Query.query(Criteria.where("_id").in(PRIV, GRP, "grp_gone")), "conversations"));
        assertFalse(record(PRIV, A).getTop(), "重复迁移不能覆盖迁移后用户自己的修改");
        assertTrue(record(PRIV, B).getTop());
        assertEquals(A, record(PRIV, B).getTargetId());
        assertEquals(B, record(PRIV, A).getTargetId());
        assertTrue(record(GRP, A).getMute(), "群文档的偏好只属于原创建者");
        assertFalse(record(GRP, B).getMute());
        assertNotNull(record(GRP, C));
        assertNotNull(record("grp_2", B), "一次性补建：现存群的所有成员都有记录");
        assertNotNull(record("grp_2", C));
        assertEquals(7, template.count(new Query(), Conversation.class));
        assertTrue(template.indexOps("conversations").getIndexInfo().stream()
                .anyMatch(index -> "uk_user_conversation".equals(index.getName()) && index.isUnique()));

        // 补建只执行一次：之后被移出群的成员不会在重启时被加回来
        records.removeRecord("grp_2", C);
        migration.afterPropertiesSet();
        assertNull(record("grp_2", C));
    }

    @Test
    void previewSummarisesNonTextMessages() {
        assertEquals("[文件]", ConversationRecordService.preview("file", "a.pdf"));
        assertEquals("[语音]", ConversationRecordService.preview("voice", "x"));
        assertEquals(ConversationRecordService.PREVIEW_MAX_LENGTH,
                ConversationRecordService.preview("text", "字".repeat(300)).length());
    }

    private PrivateMessage privateMessage(String id, String sender, String receiver, String content) {
        PrivateMessage message = new PrivateMessage();
        message.setId(id);
        message.setConversationId(PRIV);
        message.setSenderId(sender);
        message.setReceiverId(receiver);
        message.setMessageType("text");
        message.setContent(content);
        message.setTimestamp(new Date());
        return message;
    }
}
