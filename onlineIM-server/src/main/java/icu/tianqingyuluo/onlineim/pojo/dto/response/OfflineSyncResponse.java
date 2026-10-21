package icu.tianqingyuluo.onlineim.pojo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 上线 / 重连补齐：会话未读与预览，以及自客户端游标之后的消息和期间发生的编辑 / 撤回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineSyncResponse {

    /** 当前用户可见会话的权威未读数与最新预览（含离线期间的撤回 / 编辑） */
    @Builder.Default
    private List<ConversationResponse> conversations = new ArrayList<>();

    /** seq 大于客户端游标的消息（已是当前内容，含被撤回或编辑后的状态），按 seq 升序 */
    @Builder.Default
    private List<MessageResponse> messages = new ArrayList<>();

    /** 客户端已经见过、但离线期间被编辑或撤回的消息 */
    @Builder.Default
    private List<MessageResponse> revisions = new ArrayList<>();

    /** 漏收消息超过单次上限，客户端应对正在查看的会话再拉一次增量 */
    @Builder.Default
    private boolean truncated = false;
}
