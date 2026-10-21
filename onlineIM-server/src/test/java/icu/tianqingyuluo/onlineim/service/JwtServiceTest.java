package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private final JwtUtil jwtUtil = new JwtUtil("12345678901234567890123456789012");
    private final Map<String, String> redis = new ConcurrentHashMap<>();
    private JwtService jwtService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        RedisTemplate<String, String> template = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(template.opsForZSet()).thenReturn(zset);
        doAnswer(inv -> redis.put(inv.getArgument(0), inv.getArgument(1)))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(inv -> redis.get(inv.getArgument(0)));
        jwtService = new JwtService(template, jwtUtil);
    }

    @Test
    void revokingUserBlocksEveryEarlierTokenButNotLaterOnes() throws InterruptedException {
        String deviceA = "Bearer " + jwtUtil.generateToken(user("usr_1", "alice"), "a");
        String deviceB = "Bearer " + jwtUtil.generateToken(user("usr_1", "alice"), "b");
        String otherUser = "Bearer " + jwtUtil.generateToken(user("usr_2", "bob"), "c");
        assertFalse(jwtService.isBlockedToken(deviceA));

        Thread.sleep(5);
        jwtService.revokeAllTokensOfUser("usr_1");
        Thread.sleep(5);
        String fresh = "Bearer " + jwtUtil.generateToken(user("usr_1", "alice"), "a");

        assertTrue(jwtService.isBlockedToken(deviceA));
        assertTrue(jwtService.isBlockedToken(deviceB));
        assertFalse(jwtService.isBlockedToken(otherUser));
        assertFalse(jwtService.isBlockedToken(fresh));
    }

    @Test
    void garbageTokenIsNotTreatedAsRevoked() {
        assertFalse(jwtService.isRevokedForUser("Bearer not-a-jwt"));
    }

    private static UserDetails user(String id, String username) {
        return new TestUser(id, username);
    }

    private record TestUser(String id, String username) implements UserDetails, UserIDProvider {
        @Override public String getID() { return id; }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
        @Override public String getPassword() { return "unused"; }
        @Override public String getUsername() { return username; }
    }
}
