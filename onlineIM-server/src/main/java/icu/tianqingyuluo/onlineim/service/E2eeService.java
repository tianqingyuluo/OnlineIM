package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.E2eeIdentityKey;
import icu.tianqingyuluo.onlineim.pojo.document.E2eeSession;
import icu.tianqingyuluo.onlineim.pojo.dto.request.E2eeKeyUploadRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.E2eeSessionRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.E2eeKeyResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.E2eeSessionResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import icu.tianqingyuluo.onlineim.util.E2eeEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 端到端加密的公钥目录和私聊开关。服务端不接触私钥，也不解密消息。
 */
@Slf4j
@Service
public class E2eeService {

    public static final String STREAM_TYPE = "E2EE_STATE_CHANGED";
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-fA-F]{64}");
    private static final int MIN_SPKI_BYTES = 80;
    private static final int MAX_SPKI_BYTES = 200;

    private final MongoTemplate mongoTemplate;
    private final FriendService friendService;
    private final ConversationRepository conversationRepository;
    private final RedisStreamService redisStreamService;
    private final ObjectMapper objectMapper;

    public E2eeService(MongoTemplate mongoTemplate,
                       FriendService friendService,
                       ConversationRepository conversationRepository,
                       RedisStreamService redisStreamService,
                       ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.friendService = friendService;
        this.conversationRepository = conversationRepository;
        this.redisStreamService = redisStreamService;
        this.objectMapper = objectMapper;
    }

    public E2eeKeyResponse upsertKey(String userId, E2eeKeyUploadRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        if (request == null) {
            throw new IllegalArgumentException("公钥包不能为空");
        }
        String algorithm = request.getAlgorithm() == null ? "" : request.getAlgorithm().trim();
        if (!E2eeEnvelope.ALGORITHM.equals(algorithm)) {
            throw new IllegalArgumentException("暂不支持该密钥算法");
        }
        String keyId = request.getKeyId() == null ? "" : request.getKeyId().trim();
        if (!KEY_ID.matcher(keyId).matches()) {
            throw new IllegalArgumentException("密钥标识格式不正确");
        }
        String fingerprint = request.getFingerprint() == null ? "" : request.getFingerprint().trim().toLowerCase();
        if (!FINGERPRINT.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException("指纹格式不正确");
        }
        byte[] identity = decodeSpki(request.getIdentityPublicKey(), "身份公钥");
        byte[] signing = decodeSpki(request.getSigningPublicKey(), "签名公钥");
        String actual = sha256Hex(identity);
        if (!actual.equals(fingerprint)) {
            throw new IllegalArgumentException("指纹与身份公钥不一致");
        }

        E2eeIdentityKey document = E2eeIdentityKey.builder()
                .userId(userId)
                .keyId(keyId)
                .identityPublicKey(Base64.getEncoder().encodeToString(identity))
                .signingPublicKey(Base64.getEncoder().encodeToString(signing))
                .algorithm(algorithm)
                .fingerprint(fingerprint)
                .updatedAt(new Date())
                .build();
        mongoTemplate.save(document);
        return toKeyResponse(document);
    }

    public E2eeKeyResponse getKey(String requesterId, String userId) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        if (!requesterId.equals(userId) && !friendService.areFriends(requesterId, userId)) {
            throw new ForbiddenException("只能查看自己或好友的公钥");
        }
        E2eeIdentityKey document = mongoTemplate.findById(userId, E2eeIdentityKey.class);
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "对方尚未上传公钥");
        }
        return toKeyResponse(document);
    }

    public E2eeSessionResponse getSession(String userId, String conversationId) {
        assertPrivateMember(userId, conversationId);
        E2eeSession session = mongoTemplate.findById(conversationId, E2eeSession.class);
        if (session == null) {
            return E2eeSessionResponse.builder()
                    .conversationId(conversationId)
                    .enabled(false)
                    .build();
        }
        return toSessionResponse(session);
    }

    public E2eeSessionResponse setSession(String userId, String conversationId, E2eeSessionRequest request) {
        if (request == null || request.getEnabled() == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        String peer = assertPrivateMember(userId, conversationId);
        if (Boolean.TRUE.equals(request.getEnabled())) {
            if (mongoTemplate.findById(userId, E2eeIdentityKey.class) == null) {
                throw new IllegalArgumentException("请先在本设备生成并上传加密公钥");
            }
            if (mongoTemplate.findById(peer, E2eeIdentityKey.class) == null) {
                throw new IllegalArgumentException("对方还没有加密公钥，请让对方先打开一次私聊");
            }
        }
        Date now = new Date();
        E2eeSession session = E2eeSession.builder()
                .conversationId(conversationId)
                .enabled(request.getEnabled())
                .updatedBy(userId)
                .updatedAt(now)
                .build();
        mongoTemplate.save(session);
        publishState(userId, peer, session);
        return toSessionResponse(session);
    }

    public E2eeSessionResponse setSessionByPeer(String userId, String targetUserId, E2eeSessionRequest request) {
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("对方用户 ID 不能为空");
        }
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("不能和自己开启加密会话");
        }
        return setSession(userId, ConversationIdUtil.privateConversationId(userId, targetUserId), request);
    }

    private String assertPrivateMember(String userId, String conversationId) {
        if (userId == null || userId.isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        if (conversationId == null || conversationId.isBlank() || conversationId.startsWith("grp_")) {
            throw new IllegalArgumentException("只有私聊可以开关端到端加密");
        }
        String peer = ConversationIdUtil.otherParticipant(conversationId, userId);
        if (peer == null) {
            throw new ForbiddenException("无权访问该私聊会话");
        }
        if (!friendService.areFriends(userId, peer)) {
            throw new ForbiddenException("你们还不是好友，无法设置加密会话");
        }
        Conversation record = conversationRepository.findByConversationIdAndUserId(conversationId, userId);
        if (record == null || !"private".equals(record.getConversationType())) {
            throw new ForbiddenException("无权访问该私聊会话");
        }
        return peer;
    }

    private void publishState(String userId, String peer, E2eeSession session) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("conversation_id", session.getConversationId());
            payload.put("enabled", Boolean.TRUE.equals(session.getEnabled()));
            payload.put("updated_by", userId);
            payload.put("updated_at", session.getUpdatedAt() == null ? null : session.getUpdatedAt().getTime());
            redisStreamService.publishGroupMessage(
                    STREAM_TYPE,
                    userId,
                    objectMapper.writeValueAsString(payload),
                    List.of(userId, peer));
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("加密会话状态已保存，但实时通知失败: conversation={}, err={}",
                    session.getConversationId(), ex.getMessage());
        }
    }

    private static byte[] decodeSpki(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(name + "不是合法的 Base64");
        }
        if (decoded.length < MIN_SPKI_BYTES || decoded.length > MAX_SPKI_BYTES) {
            throw new IllegalArgumentException(name + "长度不正确");
        }
        return decoded;
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算公钥指纹", ex);
        }
    }

    private static E2eeKeyResponse toKeyResponse(E2eeIdentityKey document) {
        return E2eeKeyResponse.builder()
                .userId(document.getUserId())
                .keyId(document.getKeyId())
                .identityPublicKey(document.getIdentityPublicKey())
                .signingPublicKey(document.getSigningPublicKey())
                .algorithm(document.getAlgorithm())
                .fingerprint(document.getFingerprint())
                .updatedAt(document.getUpdatedAt() == null ? null : document.getUpdatedAt().getTime())
                .build();
    }

    private static E2eeSessionResponse toSessionResponse(E2eeSession session) {
        return E2eeSessionResponse.builder()
                .conversationId(session.getConversationId())
                .enabled(Boolean.TRUE.equals(session.getEnabled()))
                .updatedBy(session.getUpdatedBy())
                .updatedAt(session.getUpdatedAt() == null ? null : session.getUpdatedAt().getTime())
                .build();
    }
}
