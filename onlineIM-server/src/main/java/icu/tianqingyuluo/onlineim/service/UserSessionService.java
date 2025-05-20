package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.entity.RedisConnectionMeta;
import icu.tianqingyuluo.onlineim.util.LocalChannelRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class UserSessionService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final String SESSION_PREFIX = "user:sessions:";
    private final String NODE_PREFIX = "user:nodes:";
    private final String CHANNEL_TO_SESSION_PREFIX = "user:channel:to:session:";

    @Value("${node.ip}")
    private final String ip = "";

    public UserSessionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 存储设备会话
    public void storeDeviceSession(String token, String userID, String deviceID, String connectionID, long timeout, TimeUnit unit) {
        String key = SESSION_PREFIX + userID + ":" + deviceID;

        redisTemplate.opsForValue().set(key,
                new RedisConnectionMeta(
                        connectionID,
                        ip,
                        System.currentTimeMillis()
                )
        );
        // 更新当前节点的连接数
        redisTemplate.opsForHash().increment(NODE_PREFIX, ip,1);
    }

    public boolean isCurrentNode(String nodeIP) {
        return ip.equals(nodeIP);
    }

    public void removeDeviceSession(String userID, String deviceID, String channelID) {
        String key = SESSION_PREFIX + userID + ":" + deviceID;
        redisTemplate.delete(key);
        LocalChannelRegistry.remove(channelID);
    }

//    // 删除特定设备会话
//    public String removeDeviceSession(String channelId) {
//        String key = CHANNEL_TO_SESSION_PREFIX + channelId;
//        Set<Object> info = redisTemplate.opsForSet().members(key);
//        String userId = "";
//        String deviceId = "";
//
//        if (info != null) {
//            for (Object o : info) {
//                if (String.valueOf(o).contains("usr_")) {
//                    userId = String.valueOf(o);
//                }
//                else deviceId = String.valueOf(o);
//            }
//        }
//
//        if (userId != null && deviceId != null) {
//            String targetKey = SESSION_PREFIX + userId + ":" + deviceId + ":";
//        }
//        redisTemplate.opsForSet().remove(key);
//        return userId;
//    }

    // 删除用户所有会话
    public void removeAllSessions(String userId) {
        String sessionKey = SESSION_PREFIX + userId;
        redisTemplate.delete(sessionKey);

        // 删除所有设备元信息(使用通配符删除)
        Set<String> deviceKeys = redisTemplate.keys("user:devices:" + userId + ":*");
        if (deviceKeys != null && !deviceKeys.isEmpty()) {
            redisTemplate.delete(deviceKeys);
        }
    }

    // 获取用户当前登录设备数量
    public long getActiveDeviceCount(String userId) {
        String key = SESSION_PREFIX + userId;
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        return hashOps.size(key);
    }

//    public String getUserIdByChannelId(String channelId) {
//        String key = CHANNEL_TO_SESSION_PREFIX + channelId;
//        Set<Object> info = redisTemplate.opsForSet().members(key);
//        String userId = "";
//        if (info != null) {
//            for (Object o : info) {
//                if (String.valueOf(o).contains("usr_")) {
//                    userId = String.valueOf(o);
//                }
//            }
//        }
//        return userId;
//    }
}