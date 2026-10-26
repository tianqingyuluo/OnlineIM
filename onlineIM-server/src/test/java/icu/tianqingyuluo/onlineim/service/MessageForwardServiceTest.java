package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.MessageForwardException;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageForwardServiceTest {

    private static final String ME = "usr_me";
    private static final String PEER = "usr_peer";
    private static final String FRIEND = "usr_friend";
    private static final String STRANGER = "usr_stranger";
    private static final String GROUP = "grp_target";
    private static final String OTHER_GROUP = "grp_other";

    private PrivateMessageRepository privateRepo;
    private GroupMessageRepository groupRepo;
    private ConversationRepository conversationRepo;
    private FriendService friendService;
    private GroupMemberService groupMemberService;
    private GroupService groupService;
    private UserService userService;
    private ChatMediaService chatMediaService;
    private ConversationRecordService records;
    private RedisStreamService redis;
    private MessageService messageService;
    private MessageForwardService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String sourceConv = ConversationIdUtil.privateConversationId(ME, PEER);
    private final List<PrivateMessage> privateSources = new ArrayList<>();

    @BeforeEach
    void setUp() {
        privateRepo = mock(PrivateMessageRepository.class);
        groupRepo = mock(GroupMessageRepository.class);
        conversationRepo = mock(ConversationRepository.class);
        friendService = mock(FriendService.class);
        groupMemberService = mock(GroupMemberService.class);
        groupService = mock(GroupService.class);
        userService = mock(UserService.class);
        chatMediaService = mock(ChatMediaService.class);
        records = mock(ConversationRecordService.class);
        redis = mock(RedisStreamService.class);
        messageService = mock(MessageService.class);
        service = new MessageForwardService(privateRepo, groupRepo, conversationRepo, friendService,
                groupMemberService, groupService, userService, chatMediaService, records, redis, messageService,
                objectMapper, 3, 4, 6);

        when(conversationRepo.findByConversationIdAndUserId(sourceConv, ME))
                .thenReturn(Conversation.builder().conversationId(sourceConv).userId(ME).targetId(PEER)
                        .conversationType("private").build());
        when(friendService.areFriends(ME, FRIEND)).thenReturn(true);
        when(friendService.areFriends(ME, PEER)).thenReturn(true);
        when(groupMemberService.isGroupMember(GROUP, ME)).thenReturn(true);
        when(groupMemberService.getGroupMembers(GROUP)).thenReturn(List.of(member(ME), member(FRIEND)));
        when(userService.getUserBriefInfoByID(anyString())).thenAnswer(inv -> UserBriefResponse.builder()
                .userId(inv.getArgument(0)).nickname("昵称" + inv.<String>getArgument(0).substring(4)).build());
        when(chatMediaService.refreshForForward(anyString(), any(), any())).thenAnswer(inv ->
                new ChatMediaService.ResolvedMedia(
                        "image".equals(inv.getArgument(0)) ? "{\"file_id\":\"upl_x\",\"url\":\"/fresh.png\"}" : inv.getArgument(1),
                        inv.getArgument(2)));
        when(privateRepo.findByConversationIdAndIdIn(eq(sourceConv), anyCollection())).thenAnswer(inv -> {
            List<String> ids = new ArrayList<>(inv.<java.util.Collection<String>>getArgument(1));
            return privateSources.stream().filter(m -> ids.contains(m.getId())).toList();
        });
        when(privateRepo.save(any(PrivateMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(groupRepo.save(any(GroupMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageService.convertPrivateMessageToResponse(any())).thenAnswer(inv -> {
            PrivateMessage m = inv.getArgument(0);
            return MessageResponse.builder().messageId(m.getId()).conversationId(m.getConversationId())
                    .messageType(m.getMessageType()).content(m.getContent()).build();
        });
        when(messageService.convertGroupMessageToResponse(any())).thenAnswer(inv -> {
            GroupMessage m = inv.getArgument(0);
            return MessageResponse.builder().messageId(m.getId()).conversationId(m.getGroupId())
                    .messageType(m.getMessageType()).content(m.getContent()).build();
        });

        privateSources.add(source("msg_2", PEER, "image", "{\"file_id\":\"upl_x\",\"url\":\"/old.png\"}", "200", 0));
        privateSources.add(source("msg_1", ME, "text", "你好", "100", 0));
        privateSources.add(source("msg_3", PEER, "text", "撤回了", "300", 3));
    }

    private static GroupMemberResponse member(String id) {
        GroupMemberResponse member = new GroupMemberResponse();
        member.setUserInfo(UserBriefResponse.builder().userId(id).build());
        return member;
    }

    private PrivateMessage source(String id, String sender, String type, String content, String seq, int status) {
        PrivateMessage m = new PrivateMessage();
        m.setId(id);
        m.setConversationId(sourceConv);
        m.setSenderId(sender);
        m.setReceiverId(sender.equals(ME) ? PEER : ME);
        m.setMessageType(type);
        m.setContent(content);
        m.setExt(Map.of("file_name", "x"));
        m.setSeqId(seq);
        m.setStatus(status);
        m.setTimestamp(new Date(1_700_000_000_000L + Long.parseLong(seq)));
        return m;
    }

    private MessageForwardRequest request(List<String> ids, List<String> targets, String mode) {
        return MessageForwardRequest.builder().sourceConversationId(sourceConv).messageIds(ids)
                .targets(targets).mode(mode).clientRequestId("req1").build();
    }

    private static void assertStatus(HttpStatus status, Runnable action) {
        MessageForwardException e = assertThrows(MessageForwardException.class, action::run);
        assertEquals(status, e.getStatus(), e.getMessage());
    }

    @Test
    void separateForwardCreatesOneMessagePerSourceInSeqOrderForEveryTarget() {
        MessageForwardResponse response = service.forward(
                request(List.of("msg_2", "msg_1", "msg_2"), List.of(FRIEND, GROUP), null), ME);

        assertEquals("separate", response.getMode());
        assertEquals(2, response.getResults().size());
        MessageForwardResponse.TargetResult friend = response.getResults().get(0);
        assertEquals(FRIEND, friend.getTargetId());
        assertEquals(ConversationIdUtil.privateConversationId(ME, FRIEND), friend.getConversationId());
        assertEquals(List.of("text", "image"), friend.getMessages().stream().map(MessageResponse::getMessageType).toList());

        ArgumentCaptor<PrivateMessage> saved = ArgumentCaptor.forClass(PrivateMessage.class);
        verify(privateRepo, times(2)).save(saved.capture());
        PrivateMessage text = saved.getAllValues().get(0);
        PrivateMessage image = saved.getAllValues().get(1);
        assertEquals(ME, text.getSenderId());
        assertEquals(FRIEND, text.getReceiverId());
        assertEquals("你好", text.getContent());
        assertEquals(ME, text.getForwardFrom().getOriginalSenderId());
        assertEquals(PEER, image.getForwardFrom().getOriginalSenderId());
        assertEquals("昵称peer", image.getForwardFrom().getOriginalSenderName());
        assertEquals("msg_2", image.getForwardFrom().getSourceMessageId());
        assertTrue(image.getContent().contains("/fresh.png"), "媒体元数据由服务端重新生成");
        assertEquals("fwd:req1:" + FRIEND + ":msg_1", text.getClientMessageId());
        assertNull(text.getReplyTo());
        verify(records, times(2)).onPrivateMessage(any());
        verify(redis, times(2)).publishPrivateMessage(eq("PRIVATE_MESSAGE"), eq(ME), anyString(), eq(FRIEND));

        ArgumentCaptor<GroupMessage> groupSaved = ArgumentCaptor.forClass(GroupMessage.class);
        verify(groupRepo, times(2)).save(groupSaved.capture());
        assertNull(groupSaved.getValue().getAtUsers());
        assertEquals(GROUP, groupSaved.getValue().getGroupId());
        verify(redis, times(2)).publishGroupMessage(eq("GROUP_MESSAGE"), eq(ME), anyString(), eq(List.of(ME, FRIEND)));
    }

    @Test
    void forwardingAForwardedMessageKeepsTheOriginalAuthor() {
        privateSources.get(1).setForwardFrom(MessageForwardSnapshot.builder()
                .originalSenderId("usr_origin").originalSenderName("原作者").build());
        service.forward(request(List.of("msg_1"), List.of(FRIEND), "separate"), ME);
        ArgumentCaptor<PrivateMessage> saved = ArgumentCaptor.forClass(PrivateMessage.class);
        verify(privateRepo).save(saved.capture());
        assertEquals("usr_origin", saved.getValue().getForwardFrom().getOriginalSenderId());
        assertEquals("原作者", saved.getValue().getForwardFrom().getOriginalSenderName());
    }

    @Test
    void mergedForwardCreatesOneChatRecordPerTarget() throws Exception {
        MessageForwardResponse response = service.forward(
                request(List.of("msg_2", "msg_1"), List.of(FRIEND, GROUP), "merged"), ME);
        assertEquals(1, response.getResults().get(0).getMessages().size());
        ArgumentCaptor<PrivateMessage> saved = ArgumentCaptor.forClass(PrivateMessage.class);
        verify(privateRepo).save(saved.capture());
        PrivateMessage record = saved.getValue();
        assertEquals("chat_record", record.getMessageType());
        assertNull(record.getForwardFrom());
        assertEquals("fwd:req1:" + FRIEND + ":merged", record.getClientMessageId());
        Map<?, ?> content = objectMapper.readValue(record.getContent(), Map.class);
        assertEquals("昵称me和昵称peer的聊天记录", content.get("title"));
        assertEquals(2, content.get("count"));
        List<?> items = (List<?>) content.get("items");
        assertEquals("你好", ((Map<?, ?>) items.get(0)).get("content"));
        assertEquals("昵称peer", ((Map<?, ?>) items.get(1)).get("sender_name"));
        assertTrue(((String) ((Map<?, ?>) items.get(1)).get("content")).contains("/fresh.png"));
        verify(groupRepo).save(any(GroupMessage.class));
    }

    @Test
    void mergedTitleUsesGroupNameForGroupSource() throws Exception {
        GroupMessage g = new GroupMessage();
        g.setId("gm_1");
        g.setGroupId(OTHER_GROUP);
        g.setSenderId(FRIEND);
        g.setMessageType("text");
        g.setContent("群里的话");
        g.setSeqId("5");
        g.setStatus(0);
        when(groupMemberService.isGroupMember(OTHER_GROUP, ME)).thenReturn(true);
        when(groupRepo.findByGroupIdAndIdIn(eq(OTHER_GROUP), anyCollection())).thenReturn(List.of(g));
        when(groupService.getGroupByID(OTHER_GROUP)).thenReturn(GroupBriefResponse.builder().name("家人").build());
        service.forward(MessageForwardRequest.builder().sourceConversationId(OTHER_GROUP)
                .messageIds(List.of("gm_1")).targets(List.of(FRIEND)).mode("merged").build(), ME);
        ArgumentCaptor<PrivateMessage> saved = ArgumentCaptor.forClass(PrivateMessage.class);
        verify(privateRepo).save(saved.capture());
        assertEquals("群聊“家人”的聊天记录",
                objectMapper.readValue(saved.getValue().getContent(), Map.class).get("title"));
    }

    @Test
    void retryWithSameRequestIdReturnsExistingMessagesWithoutPublishing() {
        PrivateMessage existing = source("msg_old", ME, "text", "你好", "999", 0);
        when(privateRepo.findBySenderIdAndClientMessageId(ME, "fwd:req1:" + FRIEND + ":msg_1")).thenReturn(existing);
        MessageForwardResponse response = service.forward(request(List.of("msg_1"), List.of(FRIEND), null), ME);
        assertEquals("msg_old", response.getResults().get(0).getMessages().get(0).getMessageId());
        verify(privateRepo, never()).save(any());
        verifyNoInteractions(redis);
    }

    @Test
    void rejectionsLeaveNothingBehind() {
        // 不是来源会话参与者
        assertStatus(HttpStatus.FORBIDDEN, () -> service.forward(
                MessageForwardRequest.builder().sourceConversationId("conv_someone_else")
                        .messageIds(List.of("msg_1")).targets(List.of(FRIEND)).build(), ME));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.forward(
                MessageForwardRequest.builder().sourceConversationId(OTHER_GROUP)
                        .messageIds(List.of("msg_1")).targets(List.of(FRIEND)).build(), ME));
        // 消息不存在 / 不属于来源会话
        assertStatus(HttpStatus.NOT_FOUND, () -> service.forward(request(List.of("msg_1", "msg_x"), List.of(FRIEND), null), ME));
        // 已撤回
        assertStatus(HttpStatus.CONFLICT, () -> service.forward(request(List.of("msg_1", "msg_3"), List.of(FRIEND), null), ME));
        // 目标不是好友 / 不在群里 / 自己 / 未知前缀
        assertStatus(HttpStatus.FORBIDDEN, () -> service.forward(request(List.of("msg_1"), List.of(FRIEND, STRANGER), null), ME));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.forward(request(List.of("msg_1"), List.of(OTHER_GROUP), null), ME));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.forward(request(List.of("msg_1"), List.of(ME), null), ME));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.forward(request(List.of("msg_1"), List.of("conv_x"), null), ME));
        // 参数与上限（测试里 targets≤3，逐条≤4，合并≤6）
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.forward(request(List.of(), List.of(FRIEND), null), ME));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.forward(request(List.of("msg_1"), List.of(), null), ME));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.forward(request(List.of("msg_1"), List.of(FRIEND), "copy"), ME));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.forward(
                request(List.of("a", "b", "c", "d", "e"), List.of(FRIEND), "separate"), ME));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.forward(
                request(List.of("a", "b", "c", "d", "e", "f", "g"), List.of(FRIEND), "merged"), ME));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.forward(
                request(List.of("msg_1"), List.of(FRIEND, GROUP, PEER, "usr_4"), null), ME));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.forward(
                MessageForwardRequest.builder().sourceConversationId(sourceConv).messageIds(List.of("msg_1"))
                        .targets(List.of(FRIEND)).clientRequestId("bad id!").build(), ME));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.forward(request(List.of("msg_1"), List.of(FRIEND), null), " "));

        verify(privateRepo, never()).save(any());
        verify(groupRepo, never()).save(any());
        verifyNoInteractions(redis, records);
    }

    @Test
    void unsupportedMessageTypeIsRejected() {
        privateSources.add(source("msg_9", PEER, "system_notice", "x", "900", 0));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.forward(request(List.of("msg_9"), List.of(FRIEND), null), ME));
    }

    @Test
    void chatRecordPreviewIsShownInConversationList() {
        assertEquals("[聊天记录]", ConversationRecordService.preview("chat_record", "{\"title\":\"x\"}"));
    }

    @Test
    void encryptedPrivateMessageCannotBeForwarded() {
        PrivateMessage secret = source("msg_secret", ME, "text",
                "{\"ciphertext\":\"秘密计划\",\"iv\":\"abcd\",\"sender_key_id\":\"k\"}", "400", 1);
        secret.setIsEncrypted(true);
        privateSources.add(secret);
        MessageForwardException error = assertThrows(MessageForwardException.class,
                () -> service.forward(request(List.of("msg_secret"), List.of(FRIEND), null), ME));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertTrue(error.getMessage().contains("端到端加密"));
        verify(privateRepo, never()).save(any());
    }
}
