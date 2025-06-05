package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.mapper.UserFriendMapper;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.dto.response.*;
import icu.tianqingyuluo.onlineim.pojo.entity.Group;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.service.ConversationService;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.service.GroupService;
import icu.tianqingyuluo.onlineim.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话服务实现类
 */
@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final GroupService groupService;
    private final UserService userService;
    private final FriendService friendService;
    private final UserFriendMapper userFriendMapper;

    @Autowired
    public ConversationServiceImpl(ConversationRepository conversationRepository, GroupService groupService, UserService userService, FriendService friendService, UserFriendMapper userFriendMapper) {
        this.conversationRepository = conversationRepository;
        this.groupService = groupService;
        this.userService = userService;
        this.friendService = friendService;
        this.userFriendMapper = userFriendMapper;
    }

    @Override
    public List<ConversationResponse> getUserConversations(String userId) {
        // 按更新时间降序排序，置顶的会话排在前面
        Sort sort = Sort.by(Sort.Direction.DESC, "top", "updatedAt");
        List<Conversation> conversations = conversationRepository.findUserConversations(userId, sort);
        List<ConversationResponse> conversationResponses = new ArrayList<>();
        for (Conversation conversation : conversations) {
            if (userId.equals(conversation.getUserId())) {
                conversationResponses.add(convertToConversationResponse(conversation, userId));
            }
            else {
                conversationResponses.add(convertToConversationResponse(conversation, conversation.getTargetId()));
            }

        }
        
//        return conversations.stream()
//                .map(this::convertToConversationResponse)
//                .collect(Collectors.toList());
        return conversationResponses;
    }

    @Override
    public ConversationResponse getConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findByIdAndUserIDOrTargetId(conversationId, userId);
        if (conversation == null) {
            return null;
        }
        
        return convertToConversationResponse(conversation, userId);
    }

    @Override
    public boolean deleteConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            return false;
        }
        
        // 逻辑删除，将状态设置为0
        conversation.setStatus(0);
        conversationRepository.save(conversation);
        
        return true;
    }

    @Override
    public boolean topConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            return false;
        }
        
        conversation.setTop(true);
        conversation.setUpdatedAt(new Date());
        conversationRepository.save(conversation);
        
        return true;
    }

    @Override
    public boolean untopConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            return false;
        }
        
        conversation.setTop(false);
        conversation.setUpdatedAt(new Date());
        conversationRepository.save(conversation);
        
        return true;
    }

    @Override
    public boolean muteConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            return false;
        }
        
        conversation.setMute(true);
        conversation.setUpdatedAt(new Date());
        conversationRepository.save(conversation);
        
        return true;
    }

    @Override
    public boolean unmuteConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            return false;
        }
        
        conversation.setMute(false);
        conversation.setUpdatedAt(new Date());
        conversationRepository.save(conversation);
        
        return true;
    }

    @Override
    public boolean clearMessages(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            return false;
        }
        
        // 注意：这里只是清空未读计数，实际消息记录依然保留在数据库中
        // 如果需要真正删除消息，需要在消息表中添加对应的删除标记
        conversation.setUnreadCount(0);
        conversation.setUpdatedAt(new Date());
        conversationRepository.save(conversation);
        
        return true;
    }

    @Override
    public Map<String, Object> getUnreadCount(String userId) {
        // 使用聚合查询优化，直接从数据库获取总未读数
        int totalUnread = conversationRepository.findTotalUnreadCount(userId);
        
        // 获取每个会话的未读数
        Map<String, Integer> conversationUnread = new HashMap<>();
        List<Conversation> conversations = conversationRepository.findAllUnreadCounts(userId);
        
        for (Conversation conversation : conversations) {
            int unreadCount = conversation.getUnreadCount() != null ? conversation.getUnreadCount() : 0;
            conversationUnread.put(conversation.getId(), unreadCount);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("total_unread", totalUnread);
        result.put("conversation_unread", conversationUnread);
        
        return result;
    }

    @Override
    public ConversationResponse convertToConversationResponse(Conversation conversation, String userId) {

        String id = conversation.getId();
        String name;
        String avatarUrl = "https://img.ixintu.com/download/jpg/20200901/3e9ce3813b7199ea9588eeb920f41208_512_512.jpg!bg"; // TODO: 后期头像要换

        if (id.contains("grp_")) {
            GroupBriefResponse group = groupService.getGroupByID(id);
            name = group.getName();
            if (group.getAvatar() != null) {
                avatarUrl = group.getAvatar();
            }
        }
        else {

            String withoutPrefix = id.substring("conv_".length());

            // 按 "usr_" 拆分
            String[] parts = withoutPrefix.split("usr_");

            // 过滤掉空字符串（第一个元素是空字符串）
            String user1 = "usr_" + parts[1]; // "1231231"
            String user2 = "usr_" + parts[2]; // "456"
            String remark = userFriendMapper.getRemarkByUserIdAndFriendId(user1, user2);
            if (user1.equals(userId)) {
                id = user2;
                UserResponse user = userService.getUserInfoByUserID(user2);
                if (user.getAvatarUrl() != null) {
                    avatarUrl = user.getAvatarUrl();
                }
                if (remark != null) {
                    name = remark;
                }
                else {
                name = userService.getUserInfoByUserID(user2).getNickname();
                }
            }
            else {
                id = user1;
                UserResponse user = userService.getUserInfoByUserID(user1);
                if (user.getAvatarUrl() != null) {
                    avatarUrl = user.getAvatarUrl();
                }
                if (userFriendMapper.getRemarkByUserIdAndFriendId(user2, user1) != null) {
                    name = remark;
                }
                else  {
                    name = userService.getUserInfoByUserID(user1).getNickname();
                }
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
            lastMessage = MessagePreviewResponse.builder()
                    .messageId(conversation.getLastMessage().getMessageId())
                    .contentPreview(conversation.getLastMessage().getContent())
                    .messageType(conversation.getLastMessage().getMessageType())
                    .timestamp(dateFormat.format(conversation.getLastMessage().getTimestamp()))
                    .build();
        }
        
        // 构建会话响应对象
        return ConversationResponse.builder()
                .conversationID(conversation.getId())
                .type(conversation.getConversationType())
                .targetInfo(targetInfo)
                .lastMessage(lastMessage)
                .unreadCount(conversation.getUnreadCount())
                .isMuted(conversation.getMute())
                .isPinned(conversation.getTop())
                .lastActivityTime(dateFormat.format(conversation.getUpdatedAt()))
                .build();
    }
}
