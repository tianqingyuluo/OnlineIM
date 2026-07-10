package icu.tianqingyuluo.onlineim.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.ReadReceiptRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.ReceiptRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReceiptEventPayload;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.ConversationReadStateService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.ReceiptService;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.util.SeqIdComparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ReceiptServiceImpl implements ReceiptService {

    private static final String RECEIPT_TYPE_DELIVERED = "delivered";
    private static final String RECEIPT_EVENT = "RECEIPT";
    private static final String READ_RECEIPT_EVENT = "READ_RECEIPT";

    private final ObjectMapper objectMapper;
    private final ConversationReadStateService readStateService;
    private final PrivateMessageRepository privateMessageRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final ConversationRepository conversationRepository;
    private final GroupMemberService groupMemberService;
    private final RedisStreamService redisStreamService;

    public ReceiptServiceImpl(ObjectMapper objectMapper,
                              ConversationReadStateService readStateService,
                              PrivateMessageRepository privateMessageRepository,
                              GroupMessageRepository groupMessageRepository,
                              ConversationRepository conversationRepository,
                              GroupMemberService groupMemberService,
                              RedisStreamService redisStreamService) {
        this.objectMapper = objectMapper;
        this.readStateService = readStateService;
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.conversationRepository = conversationRepository;
        this.groupMemberService = groupMemberService;
        this.redisStreamService = redisStreamService;
    }

    @Override
    public boolean handleDelivered(String receiverId, ReceiptRequest request) {
        if (request == null || !RECEIPT_TYPE_DELIVERED.equals(request.getReceiptType())
                || isBlank(receiverId) || isBlank(request.getMessageId())
                || isBlank(request.getConversationId()) || isBlank(request.getSeqId())) {
            return false;
        }

        try {
            String seqId = SeqIdComparator.requireValid(request.getSeqId());
            List<String> targets;
            if (request.getConversationId().startsWith("grp_")) {
                GroupMessage message = groupMessageRepository.findByIdAndGroupId(
                        request.getMessageId(), request.getConversationId());
                if (message == null || !sameSeq(message.getSeqId(), seqId)
                        || !groupMemberService.isGroupMember(request.getConversationId(), receiverId)
                        || Objects.equals(message.getSenderId(), receiverId)) {
                    return false;
                }
                targets = groupMemberService.getGroupMembers(request.getConversationId()).stream()
                        .map(GroupMemberResponse::getUserInfo)
                        .filter(Objects::nonNull)
                        .map(user -> user.getUserId())
                        .filter(Objects::nonNull)
                        .filter(userId -> !Objects.equals(userId, receiverId))
                        .toList();
            } else {
                PrivateMessage message = privateMessageRepository.findByIdAndConversationId(
                        request.getMessageId(), request.getConversationId());
                if (message == null || !sameSeq(message.getSeqId(), seqId)
                        || !Objects.equals(message.getReceiverId(), receiverId)) {
                    return false;
                }
                targets = List.of(message.getSenderId());
            }

            boolean changed = readStateService.advanceDelivered(
                    request.getConversationId(), receiverId,
                    request.getConversationId().startsWith("grp_") ? "group" : "private", seqId);
            if (!changed || targets.isEmpty()) {
                return changed;
            }

            ReceiptEventPayload payload = ReceiptEventPayload.builder()
                    .receiptType(RECEIPT_TYPE_DELIVERED)
                    .messageId(request.getMessageId())
                    .conversationId(request.getConversationId())
                    .seqId(seqId)
                    .receiverId(receiverId)
                    .build();
            return publish(RECEIPT_EVENT, receiverId, payload, targets);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("处理送达回执失败: receiverId={}, conversationId={}, messageId={}, error={}",
                    receiverId, request.getConversationId(), request.getMessageId(), ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean handleRead(String readerId, ReadReceiptRequest request) {
        if (request == null || isBlank(readerId) || isBlank(request.getConversationId())
                || isBlank(request.getReadSeq())) {
            return false;
        }

        try {
            String readSeq = SeqIdComparator.requireValid(request.getReadSeq());
            String conversationId = request.getConversationId();
            if (conversationId.startsWith("grp_")) {
                if (!groupMemberService.isGroupMember(conversationId, readerId)) {
                    return false;
                }
            } else if (conversationRepository.findByIdAndUserIDOrTargetId(conversationId, readerId) == null) {
                return false;
            }
            if (!isWithinLatestSeq(conversationId, readSeq)) {
                return false;
            }
            List<String> targets = readTargets(conversationId, readerId);

            boolean changed = readStateService.advanceRead(
                    conversationId, readerId,
                    conversationId.startsWith("grp_") ? "group" : "private", readSeq);
            if (!changed || targets.isEmpty()) {
                return changed;
            }

            ReceiptEventPayload payload = ReceiptEventPayload.builder()
                    .conversationId(conversationId)
                    .readSeq(readSeq)
                    .readerId(readerId)
                    .build();
            return publish(READ_RECEIPT_EVENT, readerId, payload, targets);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("处理已读回执失败: readerId={}, conversationId={}, error={}",
                    readerId, request.getConversationId(), ex.getMessage());
            return false;
        }
    }

    private List<String> readTargets(String conversationId, String readerId) {
        if (conversationId.startsWith("grp_")) {
            return groupMemberService.getGroupMembers(conversationId).stream()
                    .map(GroupMemberResponse::getUserInfo)
                    .filter(Objects::nonNull)
                    .map(user -> user.getUserId())
                    .filter(Objects::nonNull)
                    .filter(userId -> !Objects.equals(userId, readerId))
                    .toList();
        }
        PrivateMessage latest = privateMessageRepository.findTopByConversationIdOrderBySeqIdDesc(conversationId);
        if (latest == null) {
            return List.of();
        }
        String target = Objects.equals(readerId, latest.getSenderId())
                ? latest.getReceiverId()
                : latest.getSenderId();
        return target == null ? List.of() : List.of(target);
    }

    private boolean publish(String type, String senderId, ReceiptEventPayload payload, List<String> targets)
            throws JsonProcessingException {
        String message = objectMapper.writeValueAsString(payload);
        redisStreamService.publishMessage(new icu.tianqingyuluo.onlineim.websocket.event.RedisStreamEvent(
                type, senderId, message, new ArrayList<>(targets)));
        return true;
    }


    private boolean isWithinLatestSeq(String conversationId, String seqId) {
        if (conversationId.startsWith("grp_")) {
            GroupMessage latest = groupMessageRepository.findTopByGroupIdOrderBySeqIdDesc(conversationId);
            return latest == null || latest.getSeqId() == null
                    ? "0".equals(seqId)
                    : SeqIdComparator.compare(seqId, latest.getSeqId()) <= 0;
        }
        PrivateMessage latest = privateMessageRepository.findTopByConversationIdOrderBySeqIdDesc(conversationId);
        return latest == null || latest.getSeqId() == null
                ? "0".equals(seqId)
                : SeqIdComparator.compare(seqId, latest.getSeqId()) <= 0;
    }

    private boolean sameSeq(String left, String right) {
        return left != null && right != null && SeqIdComparator.compare(left, right) == 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
