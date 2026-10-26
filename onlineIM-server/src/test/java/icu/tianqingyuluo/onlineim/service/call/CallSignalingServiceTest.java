package icu.tianqingyuluo.onlineim.service.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.WebRtcProperties;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.ConversationRecordService;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.service.PresenceConnectionStore;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallSignalingServiceTest {

    private static final String A = "usr_a";
    private static final String B = "usr_b";
    private static final String C = "usr_c";
    private static final String CALL = "call_abcdefgh";

    private final ObjectMapper mapper = new ObjectMapper();
    private final FriendService friends = mock(FriendService.class);
    private final PresenceConnectionStore presence = mock(PresenceConnectionStore.class);
    private final PrivateMessageRepository messages = mock(PrivateMessageRepository.class);
    private final ConversationRecordService conversationRecords = mock(ConversationRecordService.class);
    private final RedisStreamService streams = mock(RedisStreamService.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-26T00:00:00Z"));
    private CallSignalingService service;

    @BeforeEach
    void setUp() {
        when(friends.areFriends(anyString(), anyString())).thenReturn(true);
        when(presence.aliveCount(anyString(), anyLong())).thenReturn(1);
        WebRtcProperties properties = new WebRtcProperties();
        properties.setCallTimeoutSeconds(45);
        service = new CallSignalingService(
                new InMemoryCallSessionStore(),
                new CallRecordService(messages, conversationRecords, streams, mapper),
                friends,
                presence,
                streams,
                mapper,
                properties,
                clock);
    }

    @Test
    void inviteRoutesOfferToEverySignalOfTheCallee() throws Exception {
        invite(A, B, CALL, "video", "offer-sdp");

        verify(streams).publishGroupMessage(eq(CallFrames.INVITE), eq(A), contains("offer-sdp"), eq(List.of(B)));
        JsonNode body = published(CallFrames.INVITE);
        assertEquals(A, body.get("caller_id").asText());
        assertEquals(B, body.get("target_id").asText());
        assertEquals("video", body.get("call_type").asText());
        assertEquals(conversation(A, B), body.get("conversation_id").asText());
        verify(messages, never()).save(any());
    }

    @Test
    void nonFriendCannotInvite() {
        when(friends.areFriends(A, B)).thenReturn(false);

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> invite(A, B, CALL, "video", "offer-sdp"));

        assertEquals("你们还不是好友，无法发起通话", error.getMessage());
        verify(streams, never()).publishGroupMessage(anyString(), anyString(), anyString(), any());
        verify(messages, never()).save(any());
    }

    @Test
    void offlineCalleeBecomesMissedCall() throws Exception {
        when(presence.aliveCount(eq(B), anyLong())).thenReturn(0);

        invite(A, B, CALL, "audio", "offer-sdp");

        PrivateMessage saved = savedMessage();
        assertEquals("missed", mapper.readTree(saved.getContent()).get("call_status").asText());
        assertEquals("audio", mapper.readTree(saved.getContent()).get("call_type").asText());
        verify(conversationRecords).onPrivateMessage(saved);
        verify(streams).publishPrivateMessage(eq("PRIVATE_MESSAGE"), eq(A), anyString(), eq(B));
        JsonNode cancel = published(CallFrames.CANCEL);
        assertEquals("offline", cancel.get("reason").asText());
        assertEquals("missed", cancel.get("call_status").asText());
        verify(streams, never()).publishGroupMessage(eq(CallFrames.INVITE), anyString(), anyString(), any());
    }

    @Test
    void busyCalleeDoesNotRing() throws Exception {
        invite(C, B, "call_existing1", "audio", "offer-sdp");

        invite(A, B, CALL, "video", "offer-sdp");

        PrivateMessage saved = savedMessage();
        assertEquals("busy", mapper.readTree(saved.getContent()).get("call_status").asText());
        JsonNode busy = published(CallFrames.BUSY);
        assertEquals(CALL, busy.get("call_id").asText());
        verify(streams, never()).publishGroupMessage(eq(CallFrames.INVITE), eq(A), anyString(), any());
    }

    @Test
    void callerAlreadyInCallIsRejected() {
        invite(A, C, "call_existing1", "audio", "offer-sdp");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> invite(A, B, CALL, "video", "offer-sdp"));

        assertEquals("你正在通话中", error.getMessage());
    }

    @Test
    void answerNotifiesCallerAndOtherDevicesOfCallee() throws Exception {
        invite(A, B, CALL, "video", "offer-sdp");

        service.handleInbound(session(B, "conn_b1"), CallFrames.ANSWER, "{\"call_id\":\"" + CALL + "\",\"sdp\":\"answer-sdp\"}");

        JsonNode answer = published(CallFrames.ANSWER);
        assertEquals("answer-sdp", answer.get("sdp").asText());
        JsonNode handled = published(CallFrames.HANDLED_ELSEWHERE);
        assertEquals("answered", handled.get("call_status").asText());
        assertEquals("conn_b1", handled.get("exclude_connection_id").asText());

        IllegalArgumentException late = assertThrows(IllegalArgumentException.class, () ->
                service.handleInbound(session(B, "conn_b2"), CallFrames.ANSWER, "{\"call_id\":\"" + CALL + "\",\"sdp\":\"other\"}"));
        assertTrue(late.getMessage().contains("其他设备"));
    }

    @Test
    void rejectPersistsRejectedAndStopsOtherDevices() throws Exception {
        invite(A, B, CALL, "video", "offer-sdp");

        service.handleInbound(session(B, "conn_b1"), CallFrames.REJECT, "{\"call_id\":\"" + CALL + "\"}");

        assertEquals("rejected", mapper.readTree(savedMessage().getContent()).get("call_status").asText());
        assertEquals("rejected", published(CallFrames.REJECT).get("call_status").asText());
        assertEquals("conn_b1", published(CallFrames.HANDLED_ELSEWHERE).get("exclude_connection_id").asText());
    }

    @Test
    void cancelPersistsCancelled() throws Exception {
        invite(A, B, CALL, "audio", "offer-sdp");

        service.handleInbound(session(A, "conn_a"), CallFrames.CANCEL, "{\"call_id\":\"" + CALL + "\"}");

        JsonNode content = mapper.readTree(savedMessage().getContent());
        assertEquals("cancelled", content.get("call_status").asText());
        assertEquals("user", published(CallFrames.CANCEL).get("reason").asText());
    }

    @Test
    void timeoutPersistsMissedCall() throws Exception {
        invite(A, B, CALL, "video", "offer-sdp");
        clock.plusSeconds(45);

        service.expireDueCalls();

        JsonNode content = mapper.readTree(savedMessage().getContent());
        assertEquals("missed", content.get("call_status").asText());
        assertEquals("video", content.get("call_type").asText());
        JsonNode cancel = published(CallFrames.CANCEL);
        assertEquals("timeout", cancel.get("reason").asText());
        assertEquals("missed", cancel.get("call_status").asText());
    }

    @Test
    void hangupPersistsDurationAndNotifiesPeer() throws Exception {
        invite(A, B, CALL, "audio", "offer-sdp");
        service.handleInbound(session(B, "conn_b"), CallFrames.ANSWER, "{\"call_id\":\"" + CALL + "\",\"sdp\":\"answer-sdp\"}");
        clock.plusSeconds(83);

        service.handleInbound(session(A, "conn_a"), CallFrames.HANGUP, "{\"call_id\":\"" + CALL + "\"}");

        JsonNode content = mapper.readTree(savedMessage().getContent());
        assertEquals("completed", content.get("call_status").asText());
        assertEquals(83, content.get("duration_seconds").asInt());
        assertEquals(CALL, savedMessage().getClientMessageId());
        org.mockito.ArgumentCaptor<String> bodies = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(streams, org.mockito.Mockito.atLeastOnce()).publishGroupMessage(
                eq(CallFrames.HANGUP), eq(A), bodies.capture(), any());
        assertTrue(bodies.getAllValues().stream().anyMatch(body -> body.contains("\"duration_seconds\":83")));
        verify(conversationRecords).onPrivateMessage(any());
    }

    @Test
    void candidateIsForwardedToThePeer() throws Exception {
        invite(A, B, CALL, "video", "offer-sdp");

        service.handleInbound(session(A, "conn_a"), CallFrames.CANDIDATE,
                "{\"call_id\":\"" + CALL + "\",\"candidate\":\"cand\",\"sdp_mid\":\"0\",\"sdp_m_line_index\":0}");

        JsonNode candidate = published(CallFrames.CANDIDATE);
        assertEquals("cand", candidate.get("candidate").asText());
        assertEquals("0", candidate.get("sdp_mid").asText());
        assertEquals(0, candidate.get("sdp_m_line_index").asInt());
    }

    @Test
    void ringingAckGoesToCaller() throws Exception {
        invite(A, B, CALL, "video", "offer-sdp");

        service.handleInbound(session(B, "conn_b"), CallFrames.RINGING, "{\"call_id\":\"" + CALL + "\"}");

        assertEquals(CALL, published(CallFrames.RINGING).get("call_id").asText());
    }

    private void invite(String caller, String callee, String callId, String callType, String sdp) {
        String json = "{\"call_id\":\"" + callId + "\",\"target_id\":\"" + callee
                + "\",\"conversation_id\":\"" + conversation(caller, callee)
                + "\",\"call_type\":\"" + callType + "\",\"sdp\":\"" + sdp + "\"}";
        service.handleInbound(session(caller, "conn_" + caller), CallFrames.INVITE, json);
    }

    private PrivateMessage savedMessage() {
        org.mockito.ArgumentCaptor<PrivateMessage> captor = org.mockito.ArgumentCaptor.forClass(PrivateMessage.class);
        verify(messages).save(captor.capture());
        PrivateMessage message = captor.getValue();
        assertEquals("call", message.getMessageType());
        assertEquals(A, message.getSenderId());
        return message;
    }

    private JsonNode published(String type) throws Exception {
        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(streams).publishGroupMessage(eq(type), anyString(), body.capture(), any());
        return mapper.readTree(body.getValue());
    }

    private static WebSocketSession session(String userId, String connectionId) {
        return WebSocketSession.builder().userId(userId).connectionId(connectionId).build();
    }

    private static String conversation(String left, String right) {
        return ConversationIdUtil.privateConversationId(left, right);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void plusSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
