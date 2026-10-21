package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import icu.tianqingyuluo.onlineim.service.GroupAnnouncementReadService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.registry.SessionDelivery;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把群公告发布/更新/删除推到本节点在线成员的每一条连接。人数只留给群主和管理员。
 */
@Slf4j
@Component
public class GroupAnnouncementReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final LocalSessionRegistry sessionRegistry;
    private final GroupMemberService groupMemberService;

    public GroupAnnouncementReceiver(ObjectMapper objectMapper, LocalSessionRegistry sessionRegistry,
                                     GroupMemberService groupMemberService) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.groupMemberService = groupMemberService;
    }

    @Override
    public String getSupportedMessageType() {
        return GroupAnnouncementReadService.STREAM_ANNOUNCEMENT;
    }

    @Override
    public boolean handleMessage(WebSocketMessageEvent event) {
        return deliver(event, "GROUP_ANNOUNCEMENT");
    }

    boolean deliver(WebSocketMessageEvent event, String frameType) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }
        try {
            JsonNode payload = objectMapper.readTree(event.getMessage());
            String groupId = text(payload, "group_id");
            List<String> receivers = event.getReceiverIDs() == null ? List.of() : event.getReceiverIDs();
            for (String userId : receivers) {
                List<WebSocketSession> sessions = SessionDelivery.of(sessionRegistry, userId);
                if (sessions.isEmpty()) {
                    continue;
                }
                JsonNode body = payload.deepCopy();
                if (!isManager(groupId, userId) && body instanceof ObjectNode objectNode) {
                    objectNode.remove("read_count");
                    objectNode.remove("unread_count");
                    JsonNode announcement = objectNode.get("announcement");
                    if (announcement instanceof ObjectNode announcementNode) {
                        announcementNode.remove("read_count");
                        announcementNode.remove("unread_count");
                    }
                }
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("type", frameType);
                frame.put("message", body);
                String json = objectMapper.writeValueAsString(frame);
                for (WebSocketSession session : sessions) {
                    session.sendMessage(json);
                }
            }
            return true;
        } catch (Exception ex) {
            log.error("投递群公告通知失败: {}", ex.getMessage(), ex);
            return false;
        }
    }

    private boolean isManager(String groupId, String userId) {
        try {
            return groupId != null && groupMemberService.isAdminOrOwner(groupId, userId);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
