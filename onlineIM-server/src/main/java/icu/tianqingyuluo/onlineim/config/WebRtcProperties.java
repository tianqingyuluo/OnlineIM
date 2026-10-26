package icu.tianqingyuluo.onlineim.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 1v1 WebRTC 的 STUN/TURN 与响铃超时。
 */
@Data
@Component
@ConfigurationProperties(prefix = "im.webrtc")
public class WebRtcProperties {

    /** 英文逗号分隔。默认公共 STUN。 */
    private String stunUrls = "stun:stun.l.google.com:19302";

    /** 英文逗号分隔。为空则不下发 TURN。 */
    private String turnUrls = "";

    private String turnUsername = "";

    private String turnCredential = "";

    /**
     * coturn use-auth-secret 的共享密钥。非空时发放限时 HMAC 凭证，优先于静态用户名密码。
     */
    private String turnSecret = "";

    private int turnTtlSeconds = 86400;

    private int callTimeoutSeconds = 45;

    public int timeoutSeconds() {
        return callTimeoutSeconds > 0 ? callTimeoutSeconds : 45;
    }

    public int credentialTtlSeconds() {
        return turnTtlSeconds > 0 ? turnTtlSeconds : 86400;
    }
}
