package icu.tianqingyuluo.onlineim.service.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.WebRtcProperties;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.CallSignalRequest;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.service.PresenceConnectionStore;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 1v1 通话信令。媒体仍走 WebRTC，本服务只校验好友、占用线路，并把信令经 Redis Stream 送到对方的全部在线设备。
 */
@Slf4j
@Service
public class CallSignalingService {

    private static final Pattern CALL_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final int SDP_LIMIT = 200_000;
    private static final int CANDIDATE_LIMIT = 8_000;
    private static final int ACTIVE_TTL_SECONDS = 4 * 60 * 60;

    private final CallSessionStore store;
    private final CallRecordService records;
    private final FriendService friendService;
    private final PresenceConnectionStore presence;
    private final RedisStreamService redisStreamService;
    private final ObjectMapper objectMapper;
    private final WebRtcProperties properties;
    private final Clock clock;

    @Autowired
    public CallSignalingService(CallSessionStore store,
                                CallRecordService records,
                                FriendService friendService,
                                PresenceConnectionStore presence,
                                RedisStreamService redisStreamService,
                                ObjectMapper objectMapper,
                                WebRtcProperties properties) {
        this(store, records, friendService, presence, redisStreamService, objectMapper, properties, Clock.systemUTC());
    }

    public CallSignalingService(CallSessionStore store,
                                CallRecordService records,
                                FriendService friendService,
                                PresenceConnectionStore presence,
                                RedisStreamService redisStreamService,
                                ObjectMapper objectMapper,
                                WebRtcProperties properties,
                                Clock clock) {
        this.store = store;
        this.records = records;
        this.friendService = friendService;
        this.presence = presence;
        this.redisStreamService = redisStreamService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public boolean handleInbound(WebSocketSession session, String type, String message) {
        if (session == null || session.getUserId() == null || type == null) {
            return false;
        }
        CallSignalRequest request = read(message);
        if (CallFrames.isInvite(type)) {
            invite(session, type, request);
            return true;
        }
        CallSession call = requireCall(request.getCallId());
        String userId = session.getUserId();
        if (!call.participant(userId)) {
            throw new IllegalArgumentException("无权操作该通话");
        }
        return switch (type) {
            case CallFrames.RINGING -> ringing(session, call);
            case CallFrames.ANSWER -> answer(session, call, request);
            case CallFrames.CANDIDATE -> candidate(session, call, request);
            case CallFrames.REJECT -> reject(session, call);
            case CallFrames.CANCEL -> cancel(session, call, request.getReason());
            case CallFrames.END, CallFrames.HANGUP -> hangup(session, call, type);
            default -> throw new IllegalArgumentException("不支持的通话信令");
        };
    }

    /**
     * 响铃超时：记为未接，并通知双方停止振铃。
     */
    public void expireDueCalls() {
        List<CallSession> expired = store.claimExpired(clock.millis(), 50);
        for (CallSession session : expired) {
            finish(session, CallFrames.RECORD_MISSED, 0, CallFrames.CANCEL, "timeout", session.getCallerId());
        }
    }

    private void invite(WebSocketSession session, String type, CallSignalRequest request) {
        String callerId = session.getUserId();
        String calleeId = request.getTargetId();
        if (isBlank(calleeId) || callerId.equals(calleeId)) {
            throw new IllegalArgumentException("不能给自己发起通话");
        }
        if (!friendService.areFriends(callerId, calleeId)) {
            throw new ForbiddenException("你们还不是好友，无法发起通话");
        }
        String callType = normalizeCallType(request.getCallType());
        String conversationId = request.getConversationId();
        if (isBlank(conversationId)
                || !conversationId.equals(ConversationIdUtil.privateConversationId(callerId, calleeId))) {
            throw new IllegalArgumentException("会话与通话双方不匹配");
        }
        requireSdp(request.getSdp());
        String callId = normalizeCallId(request.getCallId());

        if (store.holdsCall(callerId)) {
            throw new IllegalArgumentException("你正在通话中");
        }
        if (presence.aliveCount(calleeId, clock.millis()) <= 0) {
            CallSession missed = draft(callId, callerId, calleeId, conversationId, callType, session.getConnectionId());
            records.record(missed, CallFrames.RECORD_MISSED, 0);
            emit(CallFrames.CANCEL, callerId, signal(callId, callerId, calleeId, conversationId, callType)
                    .reason("offline").status(CallFrames.RECORD_MISSED).build(), List.of(callerId), null);
            return;
        }

        long now = clock.millis();
        int timeout = properties.timeoutSeconds();
        CallSession call = draft(callId, callerId, calleeId, conversationId, callType, session.getConnectionId());
        call.setStatus(CallFrames.STATUS_RINGING);
        call.setCreatedAt(now);
        call.setRingDeadline(now + timeout * 1000L);

        CallSessionStore.StartResult started = store.tryStart(call, timeout + 60);
        if (started == CallSessionStore.StartResult.CALLER_BUSY) {
            throw new IllegalArgumentException("你正在通话中");
        }
        if (started == CallSessionStore.StartResult.CALLEE_BUSY) {
            records.record(call, CallFrames.RECORD_BUSY, 0);
            emit(CallFrames.BUSY, callerId, signal(callId, callerId, calleeId, conversationId, callType)
                    .status(CallFrames.RECORD_BUSY).build(), List.of(callerId), null);
            return;
        }

        try {
            emit(type, callerId, signal(callId, callerId, calleeId, conversationId, callType)
                    .sdp(request.getSdp()).build(), List.of(calleeId), null);
        } catch (RuntimeException ex) {
            store.end(callId);
            throw ex;
        }
        log.info("发起通话: callId={}, caller={}, callee={}, type={}", callId, callerId, calleeId, callType);
    }

    private boolean ringing(WebSocketSession session, CallSession call) {
        if (!session.getUserId().equals(call.getCalleeId()) || !CallFrames.STATUS_RINGING.equals(call.getStatus())) {
            throw new IllegalArgumentException("当前不能回铃");
        }
        emit(CallFrames.RINGING, session.getUserId(), Map.of(
                "call_id", call.getCallId(),
                "caller_id", call.getCallerId(),
                "target_id", call.getCalleeId()), List.of(call.getCallerId()), null);
        return true;
    }

    private boolean answer(WebSocketSession session, CallSession call, CallSignalRequest request) {
        if (!session.getUserId().equals(call.getCalleeId())) {
            throw new IllegalArgumentException("只有被叫可以接听");
        }
        requireSdp(request.getSdp());
        CallSession active = store.markActive(call.getCallId(), session.getConnectionId(), clock.millis(), ACTIVE_TTL_SECONDS);
        if (active == null) {
            throw new IllegalArgumentException("通话已被其他设备接听或已结束");
        }
        emit(CallFrames.ANSWER, session.getUserId(), Map.of(
                "call_id", active.getCallId(),
                "sdp", request.getSdp(),
                "call_type", active.getCallType()), List.of(active.getCallerId()), null);
        emit(CallFrames.HANDLED_ELSEWHERE, session.getUserId(), Map.of(
                "call_id", active.getCallId(),
                "call_status", "answered"), List.of(active.getCalleeId()), session.getConnectionId());
        return true;
    }

    private boolean candidate(WebSocketSession session, CallSession call, CallSignalRequest request) {
        if (isBlank(request.getCandidate()) || request.getCandidate().length() > CANDIDATE_LIMIT) {
            throw new IllegalArgumentException("ICE candidate 不合法");
        }
        if (!CallFrames.STATUS_RINGING.equals(call.getStatus()) && !CallFrames.STATUS_ACTIVE.equals(call.getStatus())) {
            throw new IllegalArgumentException("通话已结束");
        }
        String peer = call.peerOf(session.getUserId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("call_id", call.getCallId());
        payload.put("candidate", request.getCandidate());
        if (request.getSdpMid() != null) {
            payload.put("sdp_mid", request.getSdpMid());
        }
        if (request.getSdpMLineIndex() != null) {
            payload.put("sdp_m_line_index", request.getSdpMLineIndex());
        }
        emit(CallFrames.CANDIDATE, session.getUserId(), payload, List.of(peer), null);
        return true;
    }

    private boolean reject(WebSocketSession session, CallSession call) {
        if (!session.getUserId().equals(call.getCalleeId())) {
            throw new IllegalArgumentException("只有被叫可以拒接");
        }
        CallSession ended = store.endIfStatus(call.getCallId(), CallFrames.STATUS_RINGING);
        if (ended == null) {
            throw new IllegalArgumentException("通话已不在响铃");
        }
        records.record(ended, CallFrames.RECORD_REJECTED, 0);
        emit(CallFrames.REJECT, session.getUserId(), Map.of(
                "call_id", ended.getCallId(),
                "call_status", CallFrames.RECORD_REJECTED), List.of(ended.getCallerId()), null);
        emit(CallFrames.HANDLED_ELSEWHERE, session.getUserId(), Map.of(
                "call_id", ended.getCallId(),
                "call_status", CallFrames.RECORD_REJECTED), List.of(ended.getCalleeId()), session.getConnectionId());
        return true;
    }

    private boolean cancel(WebSocketSession session, CallSession call, String reason) {
        if (!session.getUserId().equals(call.getCallerId())) {
            throw new IllegalArgumentException("只有主叫可以取消通话");
        }
        CallSession ended = store.endIfStatus(call.getCallId(), CallFrames.STATUS_RINGING);
        if (ended == null) {
            return true;
        }
        boolean timeout = "timeout".equals(reason);
        String status = timeout ? CallFrames.RECORD_MISSED : CallFrames.RECORD_CANCELLED;
        finish(ended, status, 0, CallFrames.CANCEL, timeout ? "timeout" : "user", session.getUserId());
        return true;
    }

    private boolean hangup(WebSocketSession session, CallSession call, String type) {
        if (CallFrames.STATUS_RINGING.equals(call.getStatus())) {
            if (session.getUserId().equals(call.getCallerId())) {
                return cancel(session, call, "user");
            }
            return reject(session, call);
        }
        CallSession ended = store.endIfStatus(call.getCallId(), CallFrames.STATUS_ACTIVE);
        if (ended == null) {
            return true;
        }
        int duration = 0;
        if (ended.getAnsweredAt() > 0) {
            duration = (int) Math.max(0, (clock.millis() - ended.getAnsweredAt()) / 1000);
        }
        records.record(ended, CallFrames.RECORD_COMPLETED, duration);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("call_id", ended.getCallId());
        payload.put("call_type", ended.getCallType());
        payload.put("call_status", CallFrames.RECORD_COMPLETED);
        payload.put("duration_seconds", duration);
        String peer = ended.peerOf(session.getUserId());
        emit(type, session.getUserId(), payload, List.of(peer), null);
        emit(type, session.getUserId(), payload, List.of(session.getUserId()), session.getConnectionId());
        return true;
    }

    private void finish(CallSession session, String recordStatus, int duration, String frame, String reason, String actorId) {
        records.record(session, recordStatus, duration);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("call_id", session.getCallId());
        payload.put("caller_id", session.getCallerId());
        payload.put("target_id", session.getCalleeId());
        payload.put("conversation_id", session.getConversationId());
        payload.put("call_type", session.getCallType());
        payload.put("call_status", recordStatus);
        payload.put("duration_seconds", duration);
        payload.put("reason", reason);
        emit(frame, actorId, payload, List.of(session.getCallerId(), session.getCalleeId()), null);
    }

    private CallSession requireCall(String callId) {
        if (isBlank(callId)) {
            throw new IllegalArgumentException("缺少 call_id");
        }
        CallSession call = store.find(callId);
        if (call == null) {
            throw new IllegalArgumentException("通话不存在或已结束");
        }
        return call;
    }

    private CallSignalRequest read(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("通话信令缺少载荷");
        }
        try {
            CallSignalRequest request = objectMapper.readValue(message, CallSignalRequest.class);
            return request == null ? new CallSignalRequest() : request;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("通话信令格式错误");
        }
    }

    private void emit(String type, String actorId, Map<String, Object> payload, List<String> receivers, String excludeConnectionId) {
        Map<String, Object> body = new LinkedHashMap<>(payload);
        if (excludeConnectionId != null && !excludeConnectionId.isBlank()) {
            body.put("exclude_connection_id", excludeConnectionId);
        }
        try {
            redisStreamService.publishGroupMessage(type, actorId, objectMapper.writeValueAsString(body), receivers);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("通话信令序列化失败", ex);
        }
    }

    private static SignalBuilder signal(String callId, String callerId, String calleeId, String conversationId, String callType) {
        return new SignalBuilder(callId, callerId, calleeId, conversationId, callType);
    }

    private static CallSession draft(String callId, String callerId, String calleeId, String conversationId,
                                     String callType, String callerConnectionId) {
        return CallSession.builder()
                .callId(callId)
                .callerId(callerId)
                .calleeId(calleeId)
                .conversationId(conversationId)
                .callType(callType)
                .callerConnectionId(callerConnectionId)
                .status(CallFrames.STATUS_RINGING)
                .build();
    }

    private static String normalizeCallId(String callId) {
        if (callId == null || callId.isBlank()) {
            return "call_" + cn.hutool.core.util.IdUtil.getSnowflakeNextIdStr();
        }
        if (!CALL_ID.matcher(callId).matches()) {
            throw new IllegalArgumentException("call_id 不合法");
        }
        return callId;
    }

    private static String normalizeCallType(String callType) {
        if (CallFrames.TYPE_AUDIO.equals(callType)) {
            return CallFrames.TYPE_AUDIO;
        }
        if (CallFrames.TYPE_VIDEO.equals(callType)) {
            return CallFrames.TYPE_VIDEO;
        }
        throw new IllegalArgumentException("call_type 只能是 video 或 audio");
    }

    private static void requireSdp(String sdp) {
        if (sdp == null || sdp.isBlank() || sdp.length() > SDP_LIMIT) {
            throw new IllegalArgumentException("缺少 SDP");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class SignalBuilder {
        private final Map<String, Object> payload = new LinkedHashMap<>();

        private SignalBuilder(String callId, String callerId, String calleeId, String conversationId, String callType) {
            payload.put("call_id", callId);
            payload.put("caller_id", callerId);
            payload.put("target_id", calleeId);
            payload.put("conversation_id", conversationId);
            payload.put("call_type", callType);
        }

        private SignalBuilder sdp(String sdp) {
            payload.put("sdp", sdp);
            return this;
        }

        private SignalBuilder reason(String reason) {
            payload.put("reason", reason);
            return this;
        }

        private SignalBuilder status(String status) {
            payload.put("call_status", status);
            return this;
        }

        private Map<String, Object> build() {
            return payload;
        }
    }
}
