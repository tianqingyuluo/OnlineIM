package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.mapper.UserFriendMapper;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.dto.response.*;
import icu.tianqingyuluo.onlineim.pojo.entity.Group;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.service.ConversationService;
import icu.tianqingyuluo.onlineim.service.DraftService;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.service.GroupService;
import icu.tianqingyuluo.onlineim.service.ConversationReadStateService;
import icu.tianqingyuluo.onlineim.service.ConversationRecordService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.UserService;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话服务实现类
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final GroupService groupService;
    private final UserService userService;
    private final FriendService friendService;
    private final UserFriendMapper userFriendMapper;
    private final ConversationReadStateService readStateService;
    private final GroupMemberService groupMemberService;
    private final ConversationRecordService conversationRecords;
    private final DraftService draftService;

    @Autowired
    public ConversationServiceImpl(ConversationRepository conversationRepository,
                                   GroupService groupService,
                                   UserService userService,
                                   FriendService friendService,
                                   UserFriendMapper userFriendMapper,
                                   ConversationReadStateService readStateService,
                                   GroupMemberService groupMemberService,
                                   ConversationRecordService conversationRecords,
                                   DraftService draftService) {
        this.conversationRepository = conversationRepository;
        this.groupService = groupService;
        this.userService = userService;
        this.friendService = friendService;
        this.userFriendMapper = userFriendMapper;
        this.readStateService = readStateService;
        this.groupMemberService = groupMemberService;
        this.conversationRecords = conversationRecords;
        this.draftService = draftService;
    }

    @Override
    public List<ConversationResponse> getUserConversations(String userId) {
        // 只取当前用户自己的记录：置顶在前，再按最近活跃时间倒序
        Sort sort = Sort.by(Sort.Direction.DESC, "top", "updatedAt");
        List<Conversation> conversations = conversationRepository.findByUserIdAndStatus(userId, 1, sort);
        Map<String, String> draftPreviews = draftPreviews(userId);
        List<ConversationResponse> conversationResponses = new ArrayList<>();
        for (Conversation conversation : conversations) {
            try {
                ConversationResponse response = convertToConversationResponse(conversation, userId);
                response.setDraftPreview(draftPreviews.get(conversation.getConversationId()));
                conversationResponses.add(response);
            } catch (RuntimeException ex) {
                // 群已解散等导致目标信息缺失的记录不影响整个列表
                log.warn("跳过无法展示的会话记录: id={}, error={}", conversation.getId(), ex.getMessage());
            }
        }
        return conversationResponses;
    }

    @Override
    public ConversationResponse getConversation(String conversationId, String userId) {
        Conversation conversation = ownRecord(conversationId, userId);
        if (conversation == null) {
            return null;
        }
        ConversationResponse response = convertToConversationResponse(conversation, userId);
        response.setDraftPreview(draftService.previewOf(userId, conversationId));
        return response;
    }

    @Override
    public ConversationResponse openConversation(String conversationId, String conversationType,
                                                 String userId, String targetId) {
        Conversation own = conversationRecords.ensureRecord(conversationId, conversationType, userId, targetId, true);
        if (ConversationRecordService.TYPE_PRIVATE.equals(conversationType)) {
            // 对方也要有自己的记录（会话成员关系依据），但只在缺失时以隐藏状态创建，不改动对方已有状态
            conversationRecords.ensureRecord(conversationId, conversationType, targetId, userId, false);
        }
        ConversationResponse response = convertToConversationResponse(own, userId);
        response.setDraftPreview(draftService.previewOf(userId, conversationId));
        return response;
    }

    @Override
    public boolean deleteConversation(String conversationId, String userId) {
        // 逻辑删除，只隐藏自己的记录；对方/其他群成员不受影响
        return ownRecord(conversationId, userId) != null
                && conversationRecords.updateOwn(conversationId, userId, new Update().set("status", 0));
    }

    @Override
    public void hidePrivateConversation(String userId, String friendUserId) {
        conversationRecords.hideForAll(ConversationIdUtil.privateConversationId(userId, friendUserId));
    }

    @Override
    public boolean topConversation(String conversationId, String userId) {
        return updateOwnField(conversationId, userId, "top", true);
    }

    @Override
    public boolean untopConversation(String conversationId, String userId) {
        return updateOwnField(conversationId, userId, "top", false);
    }

    @Override
    public boolean muteConversation(String conversationId, String userId) {
        return updateOwnField(conversationId, userId, "mute", true);
    }

    @Override
    public boolean unmuteConversation(String conversationId, String userId) {
        return updateOwnField(conversationId, userId, "mute", false);
    }

    @Override
    public boolean clearMessages(String conversationId, String userId) {
        // 注意：这里只是清空自己的未读缓存，实际消息记录依然保留在数据库中
        return updateOwnField(conversationId, userId, "unreadCount", 0);
    }

    @Override
    public Map<String, Object> getUnreadCount(String userId) {
        Integer total = conversationRepository.findTotalUnreadCount(userId);

        Map<String, Integer> conversationUnread = new HashMap<>();
        for (Conversation conversation : conversationRepository.findAllUnreadCounts(userId)) {
            int unreadCount = conversation.getUnreadCount() != null ? conversation.getUnreadCount() : 0;
            conversationUnread.put(conversation.getConversationId(), unreadCount);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total_unread", total == null ? 0 : total);
        result.put("conversation_unread", conversationUnread);
        return result;
    }

    private Map<String, String> draftPreviews(String userId) {
        try {
            Map<String, String> previews = draftService.previewsOf(userId);
            return previews == null ? Map.of() : previews;
        } catch (RuntimeException ex) {
            log.warn("读取草稿摘要失败，会话列表不显示草稿: {}", ex.getMessage());
            return Map.of();
        }
    }

    private boolean updateOwnField(String conversationId, String userId, String field, Object value) {
        return ownRecord(conversationId, userId) != null
                && conversationRecords.updateOwn(conversationId, userId, new Update().set(field, value));
    }

    /**
     * 当前用户自己的会话记录。群会话以群成员关系为准：已不是成员返回 null；
     * 是成员但缺记录（例如历史数据）时补建。
     */
    private Conversation ownRecord(String conversationId, String userId) {
        if (conversationId == null || userId == null) {
            return null;
        }
        Conversation conversation = conversationRepository.findByConversationIdAndUserId(conversationId, userId);
        if (conversationId.startsWith("grp_")) {
            if (!groupMemberService.isGroupMember(conversationId, userId)) {
                return null;
            }
            if (conversation == null) {
                conversation = conversationRecords.ensureRecord(conversationId,
                        ConversationRecordService.TYPE_GROUP, userId, conversationId, true);
            }
        }
        return conversation;
    }

    @Override
    public ConversationResponse convertToConversationResponse(Conversation conversation, String userId) {

        String conversationId = conversation.getConversationId();
        String id = conversationId;
        String name;
        // 没有头像时返回空串，由前端按会话类型显示默认头像
        String avatarUrl = "";

        if (id.contains("grp_")) {
            GroupBriefResponse group = groupService.getGroupByID(id);
            name = group.getName();
            if (group.getAvatar() != null && !group.getAvatar().isBlank()) {
                avatarUrl = group.getAvatar();
            }
        }
        else {
            // 私聊会话ID为 conv_ + 两个 usr_ 用户ID按字典序拼接
            String withoutPrefix = id.substring("conv_".length());
            String[] parts = withoutPrefix.split("usr_");
            String user1 = "usr_" + parts[1];
            String user2 = "usr_" + parts[2];
            String otherUserId = user1.equals(userId) ? user2 : user1;
            id = otherUserId;
            UserResponse other = userService.getUserInfoByUserID(otherUserId);
            if (other != null && other.getAvatarUrl() != null && !other.getAvatarUrl().isBlank()) {
                avatarUrl = other.getAvatarUrl();
            }
            // 备注是当前查看者给对方设置的
            String remark = userFriendMapper.getRemarkByUserIdAndFriendId(userId, otherUserId);
            if (remark != null && !remark.isBlank()) {
                name = remark;
            }
            else {
                name = other != null ? other.getNickname() : otherUserId;
            }
        }

        // 创建目标信息（用户或群组）
        TargetInfoResponse targetInfo = TargetInfoResponse.builder()
                .id(id)
                // 这里需要根据targetId查询用户或群组信息，补充name和avatar等字段
                .name(name)
                .avatarUrl(avatarUrl)
                .build();
        
        // 创建最后一条消息的预览
        MessagePreviewResponse lastMessage = null;
        if (conversation.getLastMessage() != null && conversation.getLastMessage().getMessageId() != null) {
            String senderId = conversation.getLastMessage().getSenderId();
            lastMessage = MessagePreviewResponse.builder()
                    .messageId(conversation.getLastMessage().getMessageId())
                    .senderId(senderId)
                    .senderNickname(previewSenderName(senderId, id, name))
                    .contentPreview(conversation.getLastMessage().getContent())
                    .messageType(conversation.getLastMessage().getMessageType())
                    .timestamp(dateFormat.format(conversation.getLastMessage().getTimestamp()))
                    .isRecalled("recalled".equals(conversation.getLastMessage().getMessageType()))
                    .build();
        }
        
        // 构建会话响应对象
        String deliveredSeq = "0";
        String readSeq = "0";
        String latestSeq = "0";
        Integer unreadCount = conversation.getUnreadCount();
        try {
            var readState = readStateService.getReadState(conversationId, userId, null);
            deliveredSeq = readState.getDeliveredSeq();
            readSeq = readState.getReadSeq();
            latestSeq = readState.getLatestSeq();
            unreadCount = Math.toIntExact(readState.getUnreadCount());
        } catch (RuntimeException ignored) {
            // 历史会话可能尚未建立 read state，保留旧会话字段并在后续访问时懒创建。
        }

        // @我 以读游标为准过滤，即使清理失败也不会在已读后继续显示
        var mentions = ConversationRecordService.unreadMentions(conversation.getPendingMentions(), readSeq);
        var atAllMentions = mentions.stream()
                .filter(mention -> mention != null && Boolean.TRUE.equals(mention.getAtAll()))
                .toList();

        return ConversationResponse.builder()
                .conversationID(conversationId)
                .type(conversation.getConversationType())
                .targetInfo(targetInfo)
                .lastMessage(lastMessage)
                .unreadCount(unreadCount)
                .atMe(!mentions.isEmpty())
                .atMeCount(mentions.size())
                .atMeMessageId(mentions.isEmpty() ? null : mentions.getFirst().getMessageId())
                .atAll(!atAllMentions.isEmpty())
                .atAllCount(atAllMentions.size())
                .atAllMessageId(atAllMentions.isEmpty() ? null : atAllMentions.getFirst().getMessageId())
                .deliveredSeq(deliveredSeq)
                .readSeq(readSeq)
                .latestSeq(latestSeq)
                .isMuted(Boolean.TRUE.equals(conversation.getMute()))
                .isPinned(Boolean.TRUE.equals(conversation.getTop()))
                .lastActivityTime(conversation.getUpdatedAt() == null ? null : dateFormat.format(conversation.getUpdatedAt()))
                .build();
    }

    /**
     * 预览里的发送者名字：私聊对方用会话显示名（备注优先），其他情况用昵称（没有昵称用用户名）；
     * 旧会话记录没有 senderId 时返回 null。
     */
    private String previewSenderName(String senderId, String targetId, String conversationName) {
        if (senderId == null || senderId.isBlank()) {
            return null;
        }
        if (senderId.equals(targetId)) {
            return conversationName;
        }
        try {
            UserResponse sender = userService.getUserInfoByUserID(senderId);
            if (sender == null) {
                return null;
            }
            return sender.getNickname() != null && !sender.getNickname().isBlank() ? sender.getNickname() : sender.getUsername();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
