package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话列表MongoDB仓库接口
 * 将page/list 优化为游标分页
 */
@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    /**
     * 查询用户的所有会话
     */
    @Query("{'userId': ?0, 'status': 1}")
    List<Conversation> findUserConversations(String userId, Sort sort);
    
    /**
     * 查询用户的会话（分页）
     */
    Page<Conversation> findByUserIdAndStatusOrderByTopDescUpdatedAtDesc(String userId, Integer status, Pageable pageable);
    
    /**
     * 查询指定的会话
     */
    Conversation findByIdAndUserId(String id, String userId);
    
    /**
     * 根据用户ID和目标ID查询会话（针对单聊）
     */
    Conversation findByUserIdAndTargetIdAndConversationType(String userId, String targetId, String conversationType);
    
    /**
     * 查询用户的所有未读消息总数
     */
    @Query(value = "{'userId': ?0, 'status': 1}", fields = "{'unreadCount': 1}")
    List<Conversation> findAllUnreadCounts(String userId);
} 