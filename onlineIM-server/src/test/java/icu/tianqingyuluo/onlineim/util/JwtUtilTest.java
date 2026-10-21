package icu.tianqingyuluo.onlineim.util;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private static final String SECRET = "12345678901234567890123456789012";
    private static final String OTHER_SECRET = "abcdefghijklmnopqrstuvwxyz012345";

    @Test
    void sameConfiguredSecretVerifiesTokenAcrossInstances() {
        JwtUtil issuer = new JwtUtil(SECRET);
        JwtUtil verifier = new JwtUtil(SECRET);
        UserDetails user = User.withUsername("alice").password("unused").roles("USER").build();

        String token = issuer.generateToken(user, "device-1");

        assertEquals("alice", verifier.getUsernameFromToken("Bearer " + token));
    }

    @Test
    void differentSecretCannotReadToken() {
        JwtUtil issuer = new JwtUtil(SECRET);
        JwtUtil verifier = new JwtUtil(OTHER_SECRET);
        UserDetails user = User.withUsername("alice").password("unused").roles("USER").build();
        String token = issuer.generateToken(user, "device-1");

        assertThrows(JwtException.class, () -> verifier.getUsernameFromToken("Bearer " + token));
    }

    @Test
    void missingOrShortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil(null));
        assertThrows(IllegalStateException.class, () -> new JwtUtil("   "));
        assertThrows(IllegalStateException.class, () -> new JwtUtil("1234567890123456789012345678901"));
    }

    @Test
    void devPlaceholderIsLongEnoughToStart() {
        JwtUtil jwtUtil = new JwtUtil(JwtUtil.DEV_PLACEHOLDER_SECRET);
        UserDetails user = User.withUsername("alice").password("unused").roles("USER").build();

        String token = jwtUtil.generateToken(user, "device-1");

        assertEquals("alice", jwtUtil.getUsernameFromToken("Bearer " + token));
    }
}
