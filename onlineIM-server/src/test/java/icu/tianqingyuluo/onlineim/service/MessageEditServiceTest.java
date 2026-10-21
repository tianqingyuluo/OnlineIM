package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import icu.tianqingyuluo.onlineim.exception.MessageEditException;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.MessageEditRecord;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageEditRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageEditServiceTest {

    private static final String ME = "usr_me";
    private static final String PEER = "usr_peer";
    private static final String OTHER = "usr_other";
    private static final String GROUP = "grp_1";

    private PrivateMessageRepository privateRepo;
    private GroupMessageRepository groupRepo;
    private MongoTemplate mongoTemplate;
    private FriendService friendService;
    private GroupMemberService groupMemberService;
    private ConversationRecordService records;
    private RedisStreamService redis;
    private MessageService messageService;
    private MessageEditService service;

    @BeforeEach
    void setUp() {
        privateRepo = mock(PrivateMessageRepository.class);
        groupRepo = mock(GroupMessageRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        friendService = mock(FriendService.class);
        groupMemberService = mock(GroupMemberService.class);
        records = mock(ConversationRecordService.class);
        redis = mock(RedisStreamService.class);
        messageService = mock(MessageService.class);
        service = new MessageEditService(privateRepo, groupRepo, mongoTemplate, friendService, groupMemberService,
                records, redis, messageService, new ObjectMapper(), 120, 10);

        when(privateRepo.findById(anyString())).thenReturn(Optional.empty());
        when(groupRepo.findById(anyString())).thenReturn(Optional.empty());
        when(friendService.areFriends(ME, PEER)).thenReturn(true);
        when(groupMemberService.isGroupMember(GROUP, ME)).thenReturn(true);
        when(groupMemberService.getGroupMembers(GROUP)).thenReturn(List.of(member(ME), member(PEER), member(OTHER)));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(messageService.convertPrivateMessageToResponse(any())).thenAnswer(invocation -> {
            PrivateMessage message = invocation.getArgument(0);
            return MessageResponse.builder().messageId(message.getId()).content(message.getContent())
                    .edited(message.getEditedAt() != null).contentRevision(message.getContentRevision()).build();
        });
        when(messageService.convertGroupMessageToResponse(any())).thenAnswer(invocation -> {
            GroupMessage message = invocation.getArgument(0);
            return MessageResponse.builder().messageId(message.getId()).content(message.getContent())
                    .mentionedUserIds(message.getAtUsers())
                    .edited(message.getEditedAt() != null).contentRevision(message.getContentRevision()).build();
        });
    }

    private static GroupMemberResponse member(String userId) {
        return GroupMemberResponse.builder().userInfo(UserBriefResponse.builder().userId(userId).build()).build();
    }

    private PrivateMessage privateMessage(String type, int status, long ageMillis) {
        PrivateMessage message = PrivateMessage.builder()
                .id("msg_p").conversationId("conv_1").senderId(ME).receiverId(PEER)
                .messageType(type).content("原文").status(status).seqId("5")
                .timestamp(new Date(System.currentTimeMillis() - ageMillis))
                .build();
        when(privateRepo.findById("msg_p")).thenReturn(Optional.of(message));
        return message;
    }

    private GroupMessage groupMessage(List<String> atUsers) {
        GroupMessage message = GroupMessage.builder()
                .id("msg_g").groupId(GROUP).senderId(ME).messageType("text").content("@peer 你好")
                .status(1).seqId("9").contentRevision(1).atUsers(atUsers)
                .timestamp(new Date())
                .build();
        when(groupRepo.findById("msg_g")).thenReturn(Optional.of(message));
        return message;
    }

    private static MessageEditRequest request(String content) {
        return MessageEditRequest.builder().content(content).build();
    }

    private MessageEditException expectRejected(String messageId, MessageEditRequest request, String userId) {
        return assertThrows(MessageEditException.class, () -> service.edit(messageId, request, userId));
    }

    @Test
    void editsOwnPrivateTextKeepsOriginalAndPublishesToBothSides() throws Exception {
        PrivateMessage message = privateMessage("text", 1, 1_000);

        MessageResponse response = service.edit("msg_p", request("  改过的内容  "), ME);

        assertEquals("改过的内容", response.getContent());
        assertTrue(response.getEdited());
        assertEquals(2, response.getContentRevision());
        assertEquals(1, message.getEditHistory().size());
        assertEquals("原文", message.getEditHistory().getFirst().getContent());
        assertEquals(1, message.getEditHistory().getFirst().getRevision());

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(PrivateMessage.class));
        assertTrue(query.getValue().getQueryObject().containsKey("contentRevision"));
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertEquals("改过的内容", set.get("content"));
        assertEquals(2, set.get("contentRevision"));
        assertNotNull(set.get("editedAt"));

        verify(records).onMessageEdited("conv_1", "msg_p", "text", "改过的内容");
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).publishGroupMessage(eq("EDIT_MESSAGE"), eq(ME), payload.capture(), eq(List.of(ME, PEER)));
        assertTrue(payload.getValue().contains("\"content_revision\":2"));
        assertTrue(payload.getValue().contains("\"conversation_id\":\"conv_1\""));
    }

    @Test
    void unchangedContentIsANoOp() {
        privateMessage("text", 1, 1_000);
        MessageResponse response = service.edit("msg_p", request("原文 "), ME);
        assertEquals("原文", response.getContent());
        verifyNoInteractions(mongoTemplate, redis, records);
    }

    @Test
    void rejectsOthersRecalledNonTextExpiredAndBadContent() {
        privateMessage("text", 1, 1_000);
        MessageEditException notMine = expectRejected("msg_p", request("x"), PEER);
        assertEquals(HttpStatus.FORBIDDEN, notMine.getStatus());
        assertEquals(MessageEditException.NOT_MESSAGE_SENDER, notMine.getCode());

        assertEquals(MessageEditException.EDIT_CONTENT_INVALID, expectRejected("msg_p", request("   "), ME).getCode());
        assertEquals(MessageEditException.EDIT_CONTENT_INVALID, expectRejected("msg_p", request("12345678901"), ME).getCode());
        assertEquals(MessageEditException.EDIT_CONTENT_INVALID, expectRejected("msg_p", null, ME).getCode());

        privateMessage("text", 3, 1_000);
        MessageEditException recalled = expectRejected("msg_p", request("x"), ME);
        assertEquals(HttpStatus.CONFLICT, recalled.getStatus());
        assertEquals(MessageEditException.MESSAGE_ALREADY_RECALLED, recalled.getCode());

        privateMessage("image", 1, 1_000);
        MessageEditException image = expectRejected("msg_p", request("x"), ME);
        assertEquals(HttpStatus.BAD_REQUEST, image.getStatus());
        assertEquals(MessageEditException.EDIT_NOT_SUPPORTED, image.getCode());

        privateMessage("text", 1, 121_000);
        MessageEditException expired = expectRejected("msg_p", request("x"), ME);
        assertEquals(MessageEditException.EDIT_WINDOW_EXPIRED, expired.getCode());
        assertTrue(expired.getMessage().contains("2分钟"));

        MessageEditException missing = expectRejected("msg_missing", request("x"), ME);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());

        verifyNoInteractions(redis);
    }

    @Test
    void rejectsWhenNoLongerFriendsOrNotInGroup() {
        privateMessage("text", 1, 1_000);
        when(friendService.areFriends(ME, PEER)).thenReturn(false);
        assertEquals(MessageEditException.NOT_CONVERSATION_MEMBER, expectRejected("msg_p", request("x"), ME).getCode());

        groupMessage(List.of(PEER));
        when(groupMemberService.isGroupMember(GROUP, ME)).thenReturn(false);
        assertEquals(HttpStatus.FORBIDDEN, expectRejected("msg_g", request("x"), ME).getStatus());
        verifyNoInteractions(redis);
    }

    @Test
    void concurrentEditIsRejectedAsConflict() {
        privateMessage("text", 1, 1_000);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(PrivateMessage.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        MessageEditException conflict = expectRejected("msg_p", request("新"), ME);
        assertEquals(MessageEditException.EDIT_CONFLICT, conflict.getCode());
        verifyNoInteractions(redis);
    }

    @Test
    void groupEditReplacesMentionsAndNotifiesMembers() {
        GroupMessage message = groupMessage(List.of(PEER));
        MessageEditRequest request = MessageEditRequest.builder()
                .content("@o 找你").atUsers(List.of(OTHER, ME, "usr_stranger")).build();

        MessageResponse response = service.edit("msg_g", request, ME);

        assertEquals(List.of(OTHER), response.getMentionedUserIds());
        assertEquals(List.of(PEER), message.getEditHistory().getFirst().getAtUsers());
        verify(records).replaceMentions(eq(message), any());
        verify(records).onMessageEdited(GROUP, "msg_g", "text", "@o 找你");
        verify(redis).publishGroupMessage(eq("EDIT_MESSAGE"), eq(ME), anyString(), eq(List.of(ME, PEER, OTHER)));
    }

    @Test
    void groupEditWithoutAtUsersKeepsExistingMentions() {
        groupMessage(List.of(PEER));
        MessageResponse response = service.edit("msg_g", request("@peer 改一下"), ME);
        assertEquals(List.of(PEER), response.getMentionedUserIds());
        verify(records, never()).replaceMentions(any(), any());
    }

    @Test
    void groupEditKeepsAtAllOnlyForManagers() {
        when(groupMemberService.isAdminOrOwner(GROUP, ME)).thenReturn(true);
        GroupMessage message = groupMessage(List.of(PEER));
        MessageEditRequest request = MessageEditRequest.builder()
                .content("@所有人 看一下").atUsers(List.of("all", OTHER, "usr_stranger")).build();

        MessageResponse response = service.edit("msg_g", request, ME);

        assertEquals(List.of("all", OTHER), response.getMentionedUserIds());
        verify(records).replaceMentions(eq(message), any());

        when(groupMemberService.isAdminOrOwner(GROUP, ME)).thenReturn(false);
        groupMessage(List.of("all", PEER));
        MessageResponse stripped = service.edit("msg_g", MessageEditRequest.builder()
                .content("@所有人").atUsers(List.of("all", PEER)).build(), ME);
        assertEquals(List.of(PEER), stripped.getMentionedUserIds());
    }

    @Test
    void historyKeepsOriginalAndCapsSize() {
        List<MessageEditRecord> history = new ArrayList<>();
        for (int revision = 1; revision <= MessageEditService.MAX_HISTORY; revision++) {
            history = MessageEditService.appendHistory(history, revision, "v" + revision, null, new Date());
        }
        history = MessageEditService.appendHistory(history, 99, "latest", null, new Date());
        assertEquals(MessageEditService.MAX_HISTORY, history.size());
        assertEquals("v1", history.getFirst().getContent());
        assertEquals("latest", history.getLast().getContent());
        assertEquals("v3", history.get(1).getContent());
    }

    @Test
    void windowCanBeDisabled() {
        MessageEditService unlimited = new MessageEditService(privateRepo, groupRepo, mongoTemplate, friendService,
                groupMemberService, records, redis, messageService, new ObjectMapper(), 0, 10);
        privateMessage("text", 1, 86_400_000L);
        assertEquals("新", unlimited.edit("msg_p", request("新"), ME).getContent());
        assertEquals(120, service.getEditWindowSeconds());
    }
}
