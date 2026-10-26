package icu.tianqingyuluo.onlineim.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class E2eeServiceTest {

    private static final String ME = "usr_me";
    private static final String PEER = "usr_peer";

    private MongoTemplate mongo;
    private FriendService friends;
    private ConversationRepository conversations;
    private RedisStreamService redis;
    private E2eeService service;
    private String identity;
    private String signing;
    private String fingerprint;

    @BeforeEach
    void setUp() throws Exception {
        mongo = mock(MongoTemplate.class);
        friends = mock(FriendService.class);
        conversations = mock(ConversationRepository.class);
        redis = mock(RedisStreamService.class);
        service = new E2eeService(mongo, friends, conversations, redis, new ObjectMapper());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair first = generator.generateKeyPair();
        KeyPair second = generator.generateKeyPair();
        identity = Base64.getEncoder().encodeToString(first.getPublic().getEncoded());
        signing = Base64.getEncoder().encodeToString(second.getPublic().getEncoded());
        fingerprint = E2eeService.sha256Hex(first.getPublic().getEncoded());
        when(mongo.save(any(E2eeIdentityKey.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongo.save(any(E2eeSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void storesPublicBundleAndRejectsMismatchedFingerprint() {
        E2eeKeyResponse saved = service.upsertKey(ME, request(fingerprint));
        assertEquals(ME, saved.getUserId());
        assertEquals(fingerprint, saved.getFingerprint());
        assertEquals(E2eeEnvelope.ALGORITHM, saved.getAlgorithm());

        ArgumentCaptor<E2eeIdentityKey> captor = ArgumentCaptor.forClass(E2eeIdentityKey.class);
        verify(mongo).save(captor.capture());
        assertEquals(identity, captor.getValue().getIdentityPublicKey());
        assertFalse(captor.getValue().toString().contains("PRIVATE"));

        assertThrows(IllegalArgumentException.class, () -> service.upsertKey(ME, request("ab".repeat(32))));
        assertThrows(IllegalArgumentException.class, () -> service.upsertKey(ME, E2eeKeyUploadRequest.builder()
                .identityPublicKey(identity)
                .signingPublicKey(signing)
                .keyId("key_me")
                .algorithm("X25519")
                .fingerprint(fingerprint)
                .build()));
    }

    @Test
    void keyLookupRequiresFriendshipAndMissingKeyIsNotFound() {
        when(friends.areFriends(ME, PEER)).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.getKey(ME, PEER));

        when(friends.areFriends(ME, PEER)).thenReturn(true);
        when(mongo.findById(PEER, E2eeIdentityKey.class)).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> service.getKey(ME, PEER));

        when(mongo.findById(ME, E2eeIdentityKey.class)).thenReturn(E2eeIdentityKey.builder()
                .userId(ME).keyId("key_me").identityPublicKey(identity).signingPublicKey(signing)
                .algorithm(E2eeEnvelope.ALGORITHM).fingerprint(fingerprint).build());
        assertEquals("key_me", service.getKey(ME, ME).getKeyId());
        verify(friends, never()).areFriends(ME, ME);
    }

    @Test
    void enablingSessionRequiresBothKeysAndNotifiesBothDevices() {
        String conversationId = ConversationIdUtil.privateConversationId(ME, PEER);
        when(friends.areFriends(ME, PEER)).thenReturn(true);
        when(conversations.findByConversationIdAndUserId(conversationId, ME)).thenReturn(
                Conversation.builder().conversationId(conversationId).userId(ME).targetId(PEER)
                        .conversationType("private").build());
        when(mongo.findById(ME, E2eeIdentityKey.class)).thenReturn(E2eeIdentityKey.builder().userId(ME).build());
        when(mongo.findById(PEER, E2eeIdentityKey.class)).thenReturn(E2eeIdentityKey.builder().userId(PEER).build());

        E2eeSessionResponse disabled = service.getSession(ME, conversationId);
        assertFalse(disabled.getEnabled());

        E2eeSessionResponse enabled = service.setSession(ME, conversationId, E2eeSessionRequest.builder().enabled(true).build());
        assertTrue(enabled.getEnabled());
        assertEquals(ME, enabled.getUpdatedBy());

        verify(redis).publishGroupMessage(eq(E2eeService.STREAM_TYPE), eq(ME), anyString(), eq(List.of(ME, PEER)));

        when(mongo.findById(PEER, E2eeIdentityKey.class)).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.setSession(ME, conversationId, E2eeSessionRequest.builder().enabled(true).build()));
        assertThrows(IllegalArgumentException.class,
                () -> service.setSession(ME, "grp_1", E2eeSessionRequest.builder().enabled(false).build()));
    }

    @Test
    void canToggleByPeerUserId() {
        String conversationId = ConversationIdUtil.privateConversationId(ME, PEER);
        when(friends.areFriends(ME, PEER)).thenReturn(true);
        when(conversations.findByConversationIdAndUserId(conversationId, ME)).thenReturn(
                Conversation.builder().conversationId(conversationId).userId(ME).conversationType("private").build());

        E2eeSessionResponse response = service.setSessionByPeer(ME, PEER, E2eeSessionRequest.builder().enabled(false).build());
        assertEquals(conversationId, response.getConversationId());
        assertFalse(response.getEnabled());
        verify(redis).publishGroupMessage(eq(E2eeService.STREAM_TYPE), eq(ME), anyString(), anyList());
    }

    private E2eeKeyUploadRequest request(String fp) {
        return E2eeKeyUploadRequest.builder()
                .identityPublicKey(identity)
                .signingPublicKey(signing)
                .keyId("key_me")
                .algorithm(E2eeEnvelope.ALGORITHM)
                .fingerprint(fp)
                .build();
    }
}
