package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.ChatUpload;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.document.UserEmoji;
import icu.tianqingyuluo.onlineim.repository.ChatUploadRepository;
import icu.tianqingyuluo.onlineim.repository.UserEmojiRepository;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatFileAccessServiceTest {

    private static final String KEY = "chats/images/202610/0123456789abcdef0123456789abcdef.png";
    private static final String CONV = ConversationIdUtil.privateConversationId("usr_a", "usr_b");

    private ChatUploadRepository uploads;
    private UserEmojiRepository emojis;
    private MongoTemplate mongo;
    private GroupMemberService groups;
    private ChatFileAccessService service;

    @BeforeEach
    void setUp() {
        uploads = mock(ChatUploadRepository.class);
        emojis = mock(UserEmojiRepository.class);
        mongo = mock(MongoTemplate.class);
        groups = mock(GroupMemberService.class);
        service = new ChatFileAccessService(uploads, emojis, mongo, groups);
    }

    @Test
    void boundConversationAllowsPartiesAndRejectsOutsiders() {
        when(uploads.findByObjectKey(KEY)).thenReturn(ChatUpload.builder()
                .id("upl_1").uploaderId("usr_a").objectKey(KEY).conversationIds(List.of(CONV)).build());

        assertEquals(ChatFileAccessService.ReadDecision.ALLOW, service.decide("usr_b", KEY));
        assertEquals(ChatFileAccessService.ReadDecision.ALLOW, service.decide("usr_a", KEY));
        assertEquals(ChatFileAccessService.ReadDecision.DENY, service.decide("usr_c", KEY));
        verify(mongo, never()).find(any(Query.class), eq(PrivateMessage.class));
    }

    @Test
    void unsentUploadCanBePreviewedOnlyByUploader() {
        when(uploads.findByObjectKey(KEY)).thenReturn(ChatUpload.builder()
                .id("upl_1").uploaderId("usr_a").objectKey(KEY).build());
        when(mongo.find(any(Query.class), eq(PrivateMessage.class))).thenReturn(List.of());
        when(mongo.find(any(Query.class), eq(GroupMessage.class))).thenReturn(List.of());

        assertEquals(ChatFileAccessService.ReadDecision.ALLOW, service.decide("usr_a", KEY));
        assertEquals(ChatFileAccessService.ReadDecision.DENY, service.decide("usr_b", KEY));
    }

    @Test
    void historicalMessageWithoutBindingStillFollowsConversationMembership() {
        when(uploads.findByObjectKey(KEY)).thenReturn(ChatUpload.builder()
                .id("upl_1").uploaderId("usr_a").objectKey(KEY).conversationIds(List.of()).build());
        PrivateMessage message = new PrivateMessage();
        message.setConversationId(CONV);
        when(mongo.find(any(Query.class), eq(PrivateMessage.class))).thenReturn(List.of(message));
        when(mongo.find(any(Query.class), eq(GroupMessage.class))).thenReturn(List.of());

        assertEquals(ChatFileAccessService.ReadDecision.ALLOW, service.decide("usr_b", KEY));
        assertEquals(ChatFileAccessService.ReadDecision.DENY, service.decide("usr_c", KEY));
        verify(mongo, atLeastOnce()).updateFirst(any(), any(), eq(ChatUpload.class));
    }

    @Test
    void groupFileRequiresCurrentMembership() {
        String key = "chats/files/202610/0123456789abcdef0123456789abcdef.pdf";
        when(uploads.findByObjectKey(key)).thenReturn(ChatUpload.builder()
                .id("upl_2").uploaderId("usr_a").objectKey(key).conversationIds(List.of("grp_1")).build());
        when(groups.isGroupMember("grp_1", "usr_member")).thenReturn(true);

        assertEquals(ChatFileAccessService.ReadDecision.ALLOW, service.decide("usr_member", key));
        assertEquals(ChatFileAccessService.ReadDecision.DENY, service.decide("usr_left", key));
    }

    @Test
    void missingObjectIsAbsent() {
        when(uploads.findByObjectKey(KEY)).thenReturn(null);
        when(uploads.findByThumbnailKey(KEY)).thenReturn(null);
        when(emojis.findByObjectKey(KEY)).thenReturn(List.of());
        when(mongo.find(any(Query.class), eq(PrivateMessage.class))).thenReturn(List.of());
        when(mongo.find(any(Query.class), eq(GroupMessage.class))).thenReturn(List.of());

        assertEquals(ChatFileAccessService.ReadDecision.ABSENT, service.decide("usr_a", KEY));
    }

    @Test
    void emojiIsVisibleToOwnerAndGroupMembersOnly() {
        String key = "chats/emojis/202610/0123456789abcdef0123456789abcdef.gif";
        when(uploads.findByObjectKey(key)).thenReturn(null);
        when(uploads.findByThumbnailKey(key)).thenReturn(null);
        when(emojis.findByObjectKey(key)).thenReturn(List.of(UserEmoji.builder()
                .ownerId("usr_a").objectKey(key).conversationIds(List.of("grp_1")).build()));
        when(groups.isGroupMember("grp_1", "usr_member")).thenReturn(true);

        assertEquals(ChatFileAccessService.ReadDecision.ALLOW, service.decide("usr_a", key));
        assertEquals(ChatFileAccessService.ReadDecision.ALLOW, service.decide("usr_member", key));
        assertEquals(ChatFileAccessService.ReadDecision.DENY, service.decide("usr_out", key));
    }

    @Test
    void attachRecordsFileIdAndObjectKey() {
        service.attach("{\"file_id\":\"upl_0123456789abcdef0123456789abcdef\",\"url\":\"/onlineim/" + KEY + "\"}", CONV);

        verify(mongo, atLeastOnce()).updateFirst(any(), any(), eq(ChatUpload.class));
        verify(mongo, atLeastOnce()).updateMulti(any(), any(), eq(ChatUpload.class));
    }

    @Test
    void literalRegexEscapesDotsForMongo() {
        assertEquals("a\\.png", ChatFileAccessService.literalRegex("a.png"));
    }
}
