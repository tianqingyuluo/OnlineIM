package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.RecallLog;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.RecallMessageRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.GroupMember;
import icu.tianqingyuluo.onlineim.repository.RecallLogRepository;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class RecallMessageSender implements MessageSenderHandler {

    private final ObjectMapper objectMapper;
    private final RecallLogRepository recallLogRepository;
    private final RedisStreamService redisStreamService;
    private final GroupMemberService groupMemberService;

    public RecallMessageSender(ObjectMapper objectMapper, RecallLogRepository recallLogRepository, RedisStreamService redisStreamService, GroupMemberService groupMemberService) {
        this.objectMapper = objectMapper;
        this.recallLogRepository = recallLogRepository;
        this.redisStreamService = redisStreamService;
        this.groupMemberService = groupMemberService;
    }

    @Override
    public String getSupportedMessageType() {
        return "RECALL_MESSAGE_REQUEST";
    }

    @SneakyThrows
    @Override
    public boolean publishMessage(String message) {

        RecallMessageRequest request = objectMapper.readValue(message, RecallMessageRequest.class);
        List<String> receiverIds = new ArrayList<>();

        // 创建recalllog持久化对象
        RecallLog recallLog = new RecallLog();
        recallLog.setId("recall_" + IdUtil.getSnowflakeNextIdStr());
        recallLog.setConversationId(request.getConversationId());
        recallLog.setSeqId(IdUtil.getSnowflakeNextIdStr());
        recallLog.setMessageId(request.getMessageId());
        recallLog.setOperatorId(request.getOperatorId());
        recallLog.setRecallTime(new Date());

        recallLogRepository.save(recallLog);

        if (recallLog.getConversationId().contains("grp_")) {
            receiverIds = groupMemberService
                    .getGroupMembers(recallLog.getConversationId())
                    .stream().map(GroupMemberResponse::getUserInfo)
                    .map(UserBriefResponse::getUserId)
                    .toList();
        }

        // 推送到redis流
        redisStreamService.publishRecallLog(
                "RECALL_MESSAGE",
                request.getOperatorId(),
                objectMapper.writeValueAsString(recallLog),
                receiverIds
        );

        return true;
    }
}
