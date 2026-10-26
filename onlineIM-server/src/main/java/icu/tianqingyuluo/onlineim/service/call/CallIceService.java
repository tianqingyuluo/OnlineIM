package icu.tianqingyuluo.onlineim.service.call;

import icu.tianqingyuluo.onlineim.config.WebRtcProperties;
import icu.tianqingyuluo.onlineim.pojo.dto.response.IceServersResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 STUN/TURN 配置转换成浏览器可直接交给 RTCPeerConnection 的 iceServers。
 */
@Service
public class CallIceService {

    private final WebRtcProperties properties;
    private final Clock clock;

    @Autowired
    public CallIceService(WebRtcProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public CallIceService(WebRtcProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public IceServersResponse forUser(String userId) {
        List<IceServersResponse.IceServerItem> servers = new ArrayList<>();
        List<String> stun = urls(properties.getStunUrls());
        if (stun.isEmpty()) {
            stun = List.of("stun:stun.l.google.com:19302");
        }
        servers.add(IceServersResponse.IceServerItem.builder().urls(stun).build());

        List<String> turn = urls(properties.getTurnUrls());
        if (!turn.isEmpty()) {
            IceServersResponse.IceServerItem.IceServerItemBuilder turnServer =
                    IceServersResponse.IceServerItem.builder().urls(turn);
            String secret = properties.getTurnSecret() == null ? "" : properties.getTurnSecret().trim();
            if (!secret.isEmpty()) {
                long expiry = clock.instant().getEpochSecond() + properties.credentialTtlSeconds();
                String username = TurnCredentials.username(userId, expiry);
                turnServer.username(username).credential(TurnCredentials.credential(secret, username));
            } else if (properties.getTurnUsername() != null && !properties.getTurnUsername().isBlank()) {
                turnServer.username(properties.getTurnUsername().trim());
                if (properties.getTurnCredential() != null && !properties.getTurnCredential().isBlank()) {
                    turnServer.credential(properties.getTurnCredential());
                }
            }
            servers.add(turnServer.build());
        }

        return IceServersResponse.builder()
                .iceServers(servers)
                .callTimeoutSeconds(properties.timeoutSeconds())
                .build();
    }

    static List<String> urls(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (String part : raw.split(",")) {
            String url = part.trim();
            if (!url.isEmpty()) {
                urls.add(url);
            }
        }
        return urls;
    }
}
