package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.UserTimeline;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户消息时间线MongoDB仓库接口
 */
@Repository
public interface UserTimelineRepository extends MongoRepository<UserTimeline, String> {

    /**
     * 查询用户的所有会话消息时间线
     */
    Page<UserTimeline> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);
    
    /**
     * 查询指定会话的时间线消息
     */
    Page<UserTimeline> findByUserIdAndConversationIdOrderByTimestampDesc(String userId, String conversationId, Pageable pageable);
    
    /**
     * 查询用户的未读消息
     */
    List<UserTimeline> findByUserIdAndStatus(String userId, Integer status, Pageable pageable);
    
    /**
     * 查询指定会话的未读消息数
     */
    @Query(value = "{'userId': ?0, 'conversationId': ?1, 'status': 0}", count = true)
    Long countUnreadByConversation(String userId, String conversationId);
    
    /**
     * 查询用户所有未读消息数
     */
    @Query(value = "{'userId': ?0, 'status': 0}", count = true)
    Long countAllUnread(String userId);
    
    /**
     * 按序列号查询用户时间线
     */
    List<UserTimeline> findByUserIdAndSeqIdGreaterThanOrderBySeqIdAsc(String userId, Long seqId, Pageable pageable);
} 