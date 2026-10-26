package icu.tianqingyuluo.onlineim.service.call;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * coturn {@code use-auth-secret} 的限时凭证：username 为 {@code 过期时间戳:用户ID}，
 * credential 为 HMAC-SHA1(secret, username) 的 Base64。
 */
public final class TurnCredentials {

    private TurnCredentials() {
    }

    public static String username(String userId, long expiryEpochSeconds) {
        return expiryEpochSeconds + ":" + userId;
    }

    public static String credential(String secret, String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("无法生成 TURN 凭证", ex);
        }
    }
}
