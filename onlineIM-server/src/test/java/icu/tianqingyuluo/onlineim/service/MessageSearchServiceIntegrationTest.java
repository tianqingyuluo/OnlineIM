package icu.tianqingyuluo.onlineim.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import icu.tianqingyuluo.onlineim.config.MessageSearchMongoIndexInitializer;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.mapper.UserFriendMapper;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageSearchResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserResponse;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 集成测试：真实 MongoDB 上验证消息搜索的范围、权限、正则转义、撤回 / 编辑、分页与索引。
 */
@Testcontainers
class MessageSearchServiceIntegrationTest {
    private static final String A = "usr_a";
    private static final String B = "usr_b";
    private static final String C = "usr_c";
    private static final String AB = "conv_usr_ausr_b";
    private static final String AC = "conv_usr_ausr_c";
    private static final String BC = "conv_usr_busr_c";
    private static final String G1 = "grp_1";
    private static final String G2 = "grp_2";

    private static GenericContainer<?> mongo;
    private static MongoClient client;
    private static MongoTemplate template;

    private GroupService groupService;
    private GroupMemberService groupMemberService;
    private MessageSearchService search;
    private long clock;

    @BeforeAll
    static void startMongo() {
        mongo = new GenericContainer<>(DockerImageName.parse("mongo:7")).withExposedPorts(27017);
        mongo.start();
        client = MongoClients.create("mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017));
        template = new MongoTemplate(client, "message_search_test");
    }

    @AfterAll
    static void stopMongo() {
        if (client != null) client.close();
        if (mongo != null) mongo.stop();
    }

    @BeforeEach
    void setUp() {
        template.getDb().drop();
        clock = 1_700_000_000_000L;
        MessageService messageService = mock(MessageService.class);
        when(messageService.convertPrivateMessageToResponse(any())).thenAnswer(invocation -> {
            PrivateMessage message = invocation.getArgument(0);
            return MessageResponse.builder().messageId(message.getId()).conversationId(message.getConversationId())
                    .content(message.getContent()).messageType(message.getMessageType()).build();
        });
        when(messageService.convertGroupMessageToResponse(any())).thenAnswer(invocation -> {
            GroupMessage message = invocation.getArgument(0);
            return MessageResponse.builder().messageId(message.getId()).conversationId(message.getGroupId())
                    .content(message.getContent()).messageType(message.getMessageType()).build();
        });
        groupService = mock(GroupService.class);
        groupMemberService = mock(GroupMemberService.class);
        UserService userService = mock(UserService.class);
        UserFriendMapper friendMapper = mock(UserFriendMapper.class);
        when(userService.getUserInfoByUserID(B)).thenReturn(UserResponse.builder().userId(B).nickname("Bob").avatarUrl("b.png").build());
        when(userService.getUserInfoByUserID(C)).thenReturn(UserResponse.builder().userId(C).nickname("Cid").build());
        when(friendMapper.getRemarkByUserIdAndFriendId(A, C)).thenReturn("老 C");
        when(groupService.getGroupByID(G1)).thenReturn(GroupBriefResponse.builder().groupID(G1).name("一群").build());
        // A 在 G1，不在 G2
        when(groupService.getJoinedGroupsByUserID(A)).thenReturn(List.of(GroupResponse.builder().groupID(G1).build()));
        when(groupMemberService.isGroupMember(anyString(), anyString())).thenReturn(false);
        when(groupMemberService.isGroupMember(G1, A)).thenReturn(true);
        new MessageSearchMongoIndexInitializer(template).afterPropertiesSet();
        search = new MessageSearchService(template, messageService, groupService, groupMemberService, userService, friendMapper);

        record(AB, A, 1);
        record(AC, A, 1);
        record(BC, B, 1);
        record(G1, A, 1);
    }

    private void record(String conversationId, String userId, int status) {
        template.save(Conversation.builder().id(conversationId + ":" + userId).conversationId(conversationId)
                .userId(userId).conversationType(conversationId.startsWith("grp_") ? "group" : "private")
                .status(status).build());
    }

    private PrivateMessage priv(String id, String conversationId, String sender, String type, String content, int status) {
        PrivateMessage message = PrivateMessage.builder().id(id).conversationId(conversationId).senderId(sender)
                .messageType(type).content(content).status(status).seqId(String.valueOf(clock))
                .timestamp(new Date(clock += 1000)).build();
        return template.save(message);
    }

    private GroupMessage grp(String id, String groupId, String content) {
        GroupMessage message = GroupMessage.builder().id(id).groupId(groupId).senderId(B).messageType("text")
                .content(content).status(1).seqId(String.valueOf(clock)).timestamp(new Date(clock += 1000)).build();
        return template.save(message);
    }

    private List<String> ids(MessageSearchResponse response) {
        return response.getResults().stream().map(item -> item.getMessage().getMessageId()).toList();
    }

    @Test
    void globalSearchCoversOwnPrivateAndGroupConversationsNewestFirstWithConversationInfo() {
        priv("msg_01", AB, A, "text", "明天去爬山吗", 1);
        priv("msg_02", AC, C, "text", "爬山装备借我", 1);
        grp("msg_03", G1, "群里说爬山");
        priv("msg_04", BC, B, "text", "B 和 C 也想爬山", 1);   // A 不在这个会话
        grp("msg_05", G2, "别的群爬山");                         // A 不是成员
        priv("msg_06", AB, B, "text", "无关内容", 1);

        MessageSearchResponse response = search.search(A, "  爬山 ", null, null, null);

        assertEquals("爬山", response.getKeyword());
        assertNull(response.getConversationId());
        assertEquals(List.of("msg_03", "msg_02", "msg_01"), ids(response));
        assertFalse(response.isHasMore());
        assertNull(response.getNextCursor());
        MessageSearchResponse.Item group = response.getResults().get(0);
        assertEquals("group", group.getConversation().getType());
        assertEquals("一群", group.getConversation().getName());
        assertEquals("老 C", response.getResults().get(1).getConversation().getName());
        MessageSearchResponse.Item bob = response.getResults().get(2);
        assertEquals("Bob", bob.getConversation().getName());
        assertEquals("b.png", bob.getConversation().getAvatarUrl());
        assertEquals("明天去爬山吗", bob.getSnippet());
        assertEquals(List.of(new MessageSearchResponse.Highlight(3, 2)), bob.getHighlights());
    }

    @Test
    void excludesRecalledNonTextAndMatchesEditedContent() {
        priv("msg_r", AB, A, "text", "撤回的秘密", 3);
        priv("msg_img", AB, A, "image", "{\"name\":\"秘密.png\"}", 1);
        PrivateMessage edited = priv("msg_e", AB, A, "text", "改过之后的秘密", 1);
        edited.setEditedAt(new Date());
        template.save(edited);
        priv("msg_old", AB, A, "text", "原文", 1);

        assertEquals(List.of("msg_e"), ids(search.search(A, "秘密", null, null, null)));
        assertTrue(ids(search.search(A, "撤回", null, null, null)).isEmpty());
    }

    @Test
    void keywordIsMatchedLiterallyAndCaseInsensitively() {
        priv("msg_1", AB, A, "text", "价格 (含税) 是 $5.00?", 1);
        priv("msg_2", AB, A, "text", "价格 X含税Y 是 5000", 1);
        priv("msg_3", AB, A, "text", "OnlineIM rocks", 1);
        priv("msg_4", AB, A, "text", "a.*b 不是正则", 1);
        priv("msg_5", AB, A, "text", "反斜杠 C:\\temp", 1);

        assertEquals(List.of("msg_1"), ids(search.search(A, "(含税)", null, null, null)));
        assertEquals(List.of("msg_1"), ids(search.search(A, "$5.00?", null, null, null)));
        assertEquals(List.of("msg_3"), ids(search.search(A, "onlineim", null, null, null)));
        assertEquals(List.of("msg_4"), ids(search.search(A, "a.*b", null, null, null)));
        assertEquals(List.of("msg_5"), ids(search.search(A, "C:\\temp", null, null, null)));
        assertTrue(ids(search.search(A, ".+", null, null, null)).isEmpty());
        assertEquals(List.of("msg_4"), ids(search.search(A, ".*", null, null, null)));
    }

    @Test
    void scopedSearchRequiresAccessAndOnlySearchesThatConversation() {
        priv("msg_1", AB, A, "text", "周报", 1);
        priv("msg_2", AC, A, "text", "周报", 1);
        grp("msg_3", G1, "周报");
        grp("msg_4", G2, "周报");

        MessageSearchResponse scoped = search.search(A, "周报", AB, null, null);
        assertEquals(AB, scoped.getConversationId());
        assertEquals(List.of("msg_1"), ids(scoped));
        assertEquals(List.of("msg_3"), ids(search.search(A, "周报", G1, null, null)));
        assertThrows(ForbiddenException.class, () -> search.search(A, "周报", BC, null, null));
        assertThrows(ForbiddenException.class, () -> search.search(A, "周报", G2, null, null));
        assertThrows(ForbiddenException.class, () -> search.search(C, "周报", AB, null, null));
    }

    @Test
    void hiddenConversationsAndLeftGroupsAreExcludedFromGlobalSearchButHiddenPrivateStaysSearchableInScope() {
        priv("msg_1", AB, A, "text", "旧事", 1);
        grp("msg_2", G1, "旧事");
        record(AB, A, 0);
        assertEquals(List.of("msg_2"), ids(search.search(A, "旧事", null, null, null)));
        assertEquals(List.of("msg_1"), ids(search.search(A, "旧事", AB, null, null)));

        record(G1, A, 0);
        assertTrue(ids(search.search(A, "旧事", null, null, null)).isEmpty());

        record(G1, A, 1);
        when(groupService.getJoinedGroupsByUserID(A)).thenReturn(List.of());
        when(groupMemberService.isGroupMember(G1, A)).thenReturn(false);
        assertTrue(ids(search.search(A, "旧事", null, null, null)).isEmpty());
        assertThrows(ForbiddenException.class, () -> search.search(A, "旧事", G1, null, null));
    }

    @Test
    void cursorPaginationAcrossBothCollectionsHasNoGapsOrDuplicates() {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            expected.addFirst(i % 2 == 0 ? priv("msg_p" + i, AB, A, "text", "第" + i + "条 分页", 1).getId()
                    : grp("msg_g" + i, G1, "第" + i + "条 分页").getId());
        }
        // 同一毫秒的两条：按消息 ID 倒序
        Date same = new Date(clock += 1000);
        template.save(PrivateMessage.builder().id("msg_tie_a").conversationId(AB).senderId(A).messageType("text")
                .content("分页 同时").status(1).timestamp(same).build());
        template.save(GroupMessage.builder().id("msg_tie_b").groupId(G1).senderId(B).messageType("text")
                .content("分页 同时").status(1).timestamp(same).build());
        expected.addFirst("msg_tie_a");
        expected.addFirst("msg_tie_b");

        List<String> collected = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MessageSearchResponse page = search.search(A, "分页", null, cursor, 3);
            collected.addAll(ids(page));
            assertEquals(page.isHasMore(), page.getNextCursor() != null);
            cursor = page.getNextCursor();
            pages++;
        } while (cursor != null && pages < 10);
        assertEquals(expected, collected);
        assertEquals(3, pages);
    }

    @Test
    void rejectsBlankTooLongKeywordsAndBadCursor() {
        assertThrows(IllegalArgumentException.class, () -> search.search(A, "   ", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> search.search(A, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> search.search(A, "字".repeat(51), null, null, null));
        assertDoesNotThrow(() -> search.search(A, "字".repeat(50), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> search.search(A, "x", null, "garbage", null));
        assertThrows(IllegalArgumentException.class, () -> search.search(A, "x", null, "abc:msg_1", null));
    }

    @Test
    void pageSizeIsClampedAndIndexesExist() {
        for (int i = 0; i < 55; i++) priv("msg_" + (100 + i), AB, A, "text", "大量 " + i, 1);
        assertEquals(50, search.search(A, "大量", null, null, 500).getResults().size());
        assertEquals(20, search.search(A, "大量", null, null, null).getResults().size());
        assertEquals(1, search.search(A, "大量", null, null, 0).getResults().size());

        List<String> privateIndexes = new ArrayList<>();
        template.getCollection("private_messages").listIndexes().forEach(index -> privateIndexes.add(index.getString("name")));
        List<String> groupIndexes = new ArrayList<>();
        template.getCollection("group_messages").listIndexes().forEach(index -> groupIndexes.add(index.getString("name")));
        assertTrue(privateIndexes.contains("idx_search_conversation_type_time_id"), privateIndexes.toString());
        assertTrue(groupIndexes.contains("idx_search_group_type_time_id"), groupIndexes.toString());

        Document plan = template.getCollection("private_messages").find(new Document("conversationId", new Document("$in", List.of(AB)))
                .append("messageType", "text")).sort(new Document("timestamp", -1)).explain();
        assertTrue(plan.toJson().contains("idx_search_conversation_type_time_id"));
    }
}
