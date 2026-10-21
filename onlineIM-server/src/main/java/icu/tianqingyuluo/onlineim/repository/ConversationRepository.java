package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话列表 MongoDB 仓库接口。每个参与者一条记录，详见 docs/conversation-model.md。
 */
@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    /**
     * 查询用户自己的会话记录（按状态过滤）
     */
    List<Conversation> findByUserIdAndStatus(String userId, Integer status, Sort sort);

    /**
     * 查询某个用户在指定会话上的记录；不存在说明该用户不是（或已不是）这个会话的参与者
     */
    Conversation findByConversationIdAndUserId(String conversationId, String userId);

    /**
     * 查询会话所有参与者的记录
     */
    List<Conversation> findByConversationId(String conversationId);

    /**
     * 查询用户的所有未读消息数
     */
    @Query(value = "{'userId': ?0, 'status': 1}", fields = "{'conversationId': 1, 'unreadCount': 1}")
    List<Conversation> findAllUnreadCounts(String userId);

    /**
     * 查询用户的总未读消息数（使用聚合查询优化性能）
     */
    @Aggregation(pipeline = {
            "{ $match: { 'userId': ?0, 'status': 1 } }",
            "{ $group: { _id: null, total: { $sum: '$unreadCount' } } }"
    })
    Integer findTotalUnreadCount(String userId);
}
