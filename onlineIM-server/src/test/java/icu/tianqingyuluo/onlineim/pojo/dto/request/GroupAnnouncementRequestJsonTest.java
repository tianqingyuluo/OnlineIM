package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 网页端新建/编辑公告提交的是 {title, content, pinned}，置顶勾选必须生效。 */
class GroupAnnouncementRequestJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsPinnedFromWebForm() throws Exception {
        GroupAnnouncementRequest r = mapper.readValue("{\"title\":\"t\",\"content\":\"c\",\"pinned\":true}", GroupAnnouncementRequest.class);
        assertEquals(Boolean.TRUE, r.getPin());
    }

    @Test
    void stillAcceptsPin() throws Exception {
        GroupAnnouncementRequest r = mapper.readValue("{\"title\":\"t\",\"content\":\"c\",\"pin\":true}", GroupAnnouncementRequest.class);
        assertEquals(Boolean.TRUE, r.getPin());
    }
}
