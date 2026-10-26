package icu.tianqingyuluo.onlineim.service.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.WebRtcProperties;
import icu.tianqingyuluo.onlineim.pojo.dto.response.IceServersResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallIceServiceTest {

    @Test
    void defaultStunAndTimeout() throws Exception {
        IceServersResponse response = new CallIceService(new WebRtcProperties(), Clock.systemUTC()).forUser("usr_a");

        assertEquals(45, response.getCallTimeoutSeconds());
        assertEquals(List.of("stun:stun.l.google.com:19302"), response.getIceServers().get(0).getUrls());
        assertNull(response.getIceServers().get(0).getUsername());
        String json = new ObjectMapper().writeValueAsString(response);
        assertTrue(json.contains("\"ice_servers\""));
        assertTrue(json.contains("\"call_timeout_seconds\":45"));
        assertFalse(json.contains("username"));
    }

    @Test
    void staticTurnCredentialAndCustomTimeout() {
        WebRtcProperties properties = new WebRtcProperties();
        properties.setStunUrls("stun:a.example, stun:b.example");
        properties.setTurnUrls("turn:turn.example.com:3478");
        properties.setTurnUsername("alice");
        properties.setTurnCredential("secret-pass");
        properties.setCallTimeoutSeconds(30);

        IceServersResponse response = new CallIceService(properties, Clock.systemUTC()).forUser("usr_a");

        assertEquals(30, response.getCallTimeoutSeconds());
        assertEquals(List.of("stun:a.example", "stun:b.example"), response.getIceServers().get(0).getUrls());
        IceServersResponse.IceServerItem turn = response.getIceServers().get(1);
        assertEquals(List.of("turn:turn.example.com:3478"), turn.getUrls());
        assertEquals("alice", turn.getUsername());
        assertEquals("secret-pass", turn.getCredential());
    }

    @Test
    void turnSecretIssuesTimeLimitedHmacCredential() {
        WebRtcProperties properties = new WebRtcProperties();
        properties.setTurnUrls("turn:turn.example.com:3478,turns:turn.example.com:5349");
        properties.setTurnUsername("ignored");
        properties.setTurnCredential("ignored");
        properties.setTurnSecret("secret");
        properties.setTurnTtlSeconds(3600);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L - 3600), ZoneOffset.UTC);

        IceServersResponse.IceServerItem turn = new CallIceService(properties, clock).forUser("usr_a").getIceServers().get(1);

        assertEquals("1700000000:usr_a", turn.getUsername());
        assertEquals("QEKSrLz7dcMJhk6xxehGOge+PdU=", turn.getCredential());
        assertEquals(List.of("turn:turn.example.com:3478", "turns:turn.example.com:5349"), turn.getUrls());
    }
}
