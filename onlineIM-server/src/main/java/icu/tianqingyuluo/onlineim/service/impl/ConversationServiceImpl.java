package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ConversationResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessagePreviewResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.TargetInfoResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.service.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话服务实现类
 */
@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public ConversationServiceImpl(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Override
    public List<ConversationResponse> getUserConversations(String userId) {
        // 按更新时间降序排序，置顶的会话排在前面
        Sort sort = Sort.by(Sort.Direction.DESC, "top", "updatedAt");
        List<Conversation> conversations = conversationRepository.findUserConversations(userId, sort);
        
        return conversations.stream()
                .map(this::convertToConversationResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ConversationResponse getConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            return null;
        }
        
        return convertToConversationResponse(conversation);
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
    public ConversationResponse convertToConversationResponse(Conversation conversation) {
        // 创建目标信息（用户或群组）
        TargetInfoResponse targetInfo = TargetInfoResponse.builder()
                .id(conversation.getTargetId())
                // 这里需要根据targetId查询用户或群组信息，补充name和avatar等字段
                .name("用户" + conversation.getTargetId())
                .avatarUrl("https://example.com/avatar.jpg")
                .build();
        
        // 创建最后一条消息的预览
        MessagePreviewResponse lastMessage = null;
        if (conversation.getLastMessage() != null) {
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
