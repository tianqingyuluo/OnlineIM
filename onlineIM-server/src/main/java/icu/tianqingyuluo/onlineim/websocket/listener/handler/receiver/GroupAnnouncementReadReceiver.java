package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.GroupAnnouncementReadService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import org.springframework.stereotype.Component;

/**
 * 有人确认公告已读。人数只推给群主和管理员，确认者自己收到不含人数的帧以便其他端关掉公告条。
 */
@Component
public class GroupAnnouncementReadReceiver extends GroupAnnouncementReceiver {

    public GroupAnnouncementReadReceiver(ObjectMapper objectMapper, LocalSessionRegistry sessionRegistry,
                                         GroupMemberService groupMemberService) {
        super(objectMapper, sessionRegistry, groupMemberService);
    }

    @Override
    public String getSupportedMessageType() {
        return GroupAnnouncementReadService.STREAM_READ;
    }

    @Override
    public boolean handleMessage(icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent event) {
        return deliver(event, "GROUP_ANNOUNCEMENT_READ");
    }
}
