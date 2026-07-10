package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.pojo.document.ConversationReadState;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageReadersResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReadStateResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationReadStateRepository;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.ConversationReadStateService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.UserService;
import icu.tianqingyuluo.onlineim.util.SeqIdComparator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class ConversationReadStateServiceImpl implements ConversationReadStateService {

    private final ConversationReadStateRepository readStateRepository;
    private final ConversationRepository conversationRepository;
    private final PrivateMessageRepository privateMessageRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final GroupMemberService groupMemberService;
    private final UserService userService;

    public ConversationReadStateServiceImpl(ConversationReadStateRepository readStateRepository,
                                            ConversationRepository conversationRepository,
                                            PrivateMessageRepository privateMessageRepository,
                                            GroupMessageRepository groupMessageRepository,
                                            GroupMemberService groupMemberService,
                                            UserService userService) {
        this.readStateRepository = readStateRepository;
        this.conversationRepository = conversationRepository;
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.groupMemberService = groupMemberService;
        this.userService = userService;
    }

    @Override
    public ConversationReadState getOrCreate(String conversationId, String userId, String conversationType) {
        if (conversationId == null || conversationId.isBlank() || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("conversationId 和 userId 不能为空");
        }

        return readStateRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseGet(() -> {
                    ConversationReadState state = ConversationReadState.builder()
                            .id(stateId(conversationId, userId))
                            .conversationId(conversationId)
                            .userId(userId)
                            .conversationType(conversationType != null ? conversationType : inferType(conversationId))
                            .deliveredSeq("0")
                            .readSeq("0")
                            .updatedAt(new Date())
                            .build();
                    return readStateRepository.save(state);
                });
    }

    @Override
    public boolean advanceDelivered(String conversationId, String userId, String conversationType, String deliveredSeq) {
        String normalized = SeqIdComparator.requireValid(deliveredSeq);
        return updateState(conversationId, userId, conversationType, state -> {
            String next = SeqIdComparator.max(state.getDeliveredSeq(), normalized);
            state.setDeliveredSeq(next);
        });
    }

    @Override
    public boolean advanceRead(String conversationId, String userId, String conversationType, String readSeq) {
        String normalized = SeqIdComparator.requireValid(readSeq);
        return updateState(conversationId, userId, conversationType, state -> {
            String nextRead = SeqIdComparator.max(state.getReadSeq(), normalized);
            state.setReadSeq(nextRead);
            // 已读代表客户端已经拿到并处理了该消息；允许同一请求补齐 deliveredSeq。
            state.setDeliveredSeq(SeqIdComparator.max(state.getDeliveredSeq(), nextRead));
        });
    }

    @Override
    public ReadStateResponse getReadState(String conversationId, String userId, String fromSeqId) {
        assertConversationMember(conversationId, userId);
        ConversationReadState state = getOrCreate(conversationId, userId, inferType(conversationId));
        String from = fromSeqId == null || fromSeqId.isBlank()
                ? state.getReadSeq()
                : SeqIdComparator.requireValid(fromSeqId);
        String latestSeq = latestSeq(conversationId);
        long unreadCount = unreadCount(conversationId, from, userId);

        return ReadStateResponse.builder()
                .conversationId(conversationId)
                .deliveredSeq(state.getDeliveredSeq())
                .readSeq(state.getReadSeq())
                .latestSeq(latestSeq)
                .unreadCount(unreadCount)
                .build();
    }

    @Override
    public MessageReadersResponse getMessageReaders(String conversationId, String messageId, String userId) {
        assertConversationMember(conversationId, userId);
        if (conversationId.startsWith("grp_")) {
            GroupMessage message = groupMessageRepository.findByIdAndGroupId(messageId, conversationId);
            if (message == null) {
                return null;
            }
            List<GroupMemberResponse> members = groupMemberService.getGroupMembers(conversationId);
            List<ConversationReadState> states = readStateRepository.findByConversationId(conversationId);
            Map<String, ConversationReadState> stateByUser = states.stream()
                    .collect(Collectors.toMap(ConversationReadState::getUserId, value -> value, (left, right) -> right));
            List<UserBriefResponse> readers = new ArrayList<>();
            List<UserBriefResponse> deliveredReaders = new ArrayList<>();
            for (GroupMemberResponse member : members) {
                UserBriefResponse user = member.getUserInfo();
                if (user == null || user.getUserId() == null) {
                    continue;
                }
                ConversationReadState state = stateByUser.get(user.getUserId());
                if (state == null) {
                    continue;
                }
                String deliveredSeq = SeqIdComparator.max(state.getDeliveredSeq(), "0");
                String readSeq = SeqIdComparator.max(state.getReadSeq(), "0");
                if (SeqIdComparator.compare(deliveredSeq, message.getSeqId()) >= 0) {
                    deliveredReaders.add(user);
                }
                if (SeqIdComparator.compare(readSeq, message.getSeqId()) >= 0) {
                    readers.add(user);
                }
            }
            return MessageReadersResponse.builder()
                    .conversationId(conversationId)
                    .messageId(messageId)
                    .seqId(message.getSeqId())
                    .readers(readers)
                    .deliveredReaders(deliveredReaders)
                    .build();
        }

        PrivateMessage message = privateMessageRepository.findByIdAndConversationId(messageId, conversationId);
        if (message == null) {
            return null;
        }
        String otherUserId = Objects.equals(userId, message.getSenderId())
                ? message.getReceiverId()
                : message.getSenderId();
        ConversationReadState state = readStateRepository
                .findByConversationIdAndUserId(conversationId, otherUserId)
                .orElse(null);
        List<UserBriefResponse> readers = new ArrayList<>();
        List<UserBriefResponse> deliveredReaders = new ArrayList<>();
        UserBriefResponse otherUser = userService.getUserBriefInfoByID(otherUserId);
        if (state != null && otherUser != null) {
            String deliveredSeq = SeqIdComparator.max(state.getDeliveredSeq(), "0");
            String readSeq = SeqIdComparator.max(state.getReadSeq(), "0");
            if (SeqIdComparator.compare(deliveredSeq, message.getSeqId()) >= 0) {
                deliveredReaders.add(otherUser);
            }
            if (SeqIdComparator.compare(readSeq, message.getSeqId()) >= 0) {
                readers.add(otherUser);
            }
        }
        return MessageReadersResponse.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .seqId(message.getSeqId())
                .readers(readers)
                .deliveredReaders(deliveredReaders)
                .build();
    }

    private boolean updateState(String conversationId, String userId, String conversationType,
                                Consumer<ConversationReadState> updater) {
        ConversationReadState state = getOrCreate(conversationId, userId, conversationType);
        String beforeDelivered = state.getDeliveredSeq();
        String beforeRead = state.getReadSeq();
        updater.accept(state);
        boolean changed = !Objects.equals(beforeDelivered, state.getDeliveredSeq())
                || !Objects.equals(beforeRead, state.getReadSeq());
        if (changed) {
            state.setUpdatedAt(new Date());
            readStateRepository.save(state);
        }
        return changed;
    }

    private void assertConversationMember(String conversationId, String userId) {
        if (conversationId == null || userId == null) {
            throw new IllegalArgumentException("会话和用户不能为空");
        }
        if (conversationId.startsWith("grp_")) {
            if (!groupMemberService.isGroupMember(conversationId, userId)) {
                throw new IllegalArgumentException("用户不是该群成员");
            }
            return;
        }
        if (conversationRepository.findByIdAndUserIDOrTargetId(conversationId, userId) == null) {
            throw new IllegalArgumentException("用户无权访问该会话");
        }
    }

    private long unreadCount(String conversationId, String fromSeqId, String userId) {
        if (conversationId.startsWith("grp_")) {
            return groupMessageRepository.countUnreadByGroupIdAndUserIdAndSeqIdGreaterThan(
                    conversationId, userId, fromSeqId);
        }
        return privateMessageRepository.countUnreadByConversationIdAndReceiverIdAndSeqIdGreaterThan(
                conversationId, userId, fromSeqId);
    }

    private String latestSeq(String conversationId) {
        if (conversationId.startsWith("grp_")) {
            GroupMessage message = groupMessageRepository.findTopByGroupIdOrderBySeqIdDesc(conversationId);
            return message == null || message.getSeqId() == null ? "0" : message.getSeqId();
        }
        PrivateMessage message = privateMessageRepository.findTopByConversationIdOrderBySeqIdDesc(conversationId);
        return message == null || message.getSeqId() == null ? "0" : message.getSeqId();
    }

    private String stateId(String conversationId, String userId) {
        return conversationId + ":" + userId;
    }

    private String inferType(String conversationId) {
        return conversationId != null && conversationId.startsWith("grp_") ? "group" : "private";
    }
}
