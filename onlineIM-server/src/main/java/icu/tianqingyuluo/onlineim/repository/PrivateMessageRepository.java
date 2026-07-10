package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Collection;
import java.util.List;

/**
 * 单聊消息MongoDB仓库接口
 */
@Repository
public interface PrivateMessageRepository extends MongoRepository<PrivateMessage, String> {

    /**
     * 根据会话ID查询消息历史
     */
    Page<PrivateMessage> findByConversationIdOrderByTimestampDesc(String conversationId, Pageable pageable);
    
    /**
     * 查询指定消息ID之前的历史消息
     */
    @Query("{'conversationId': ?0, 'timestamp': {$lt: ?1}}")
    List<PrivateMessage> findMessagesBeforeTimestamp(String conversationId, Date timestamp, Pageable pageable);
    
    /**
     * 查询指定消息ID之后的新消息
     */
    @Query("{'conversationId': ?0, 'timestamp': {$gt: ?1}}")
    List<PrivateMessage> findMessagesAfterTimestamp(String conversationId, Date timestamp, Pageable pageable);
    
    /**
     * 根据客户端消息ID查询消息
     */
    PrivateMessage findByClientMessageId(String clientMessageId);

    /**
     * 按发送者和客户端消息ID查询，避免不同发送者使用相同客户端ID时误去重。
     */
    PrivateMessage findBySenderIdAndClientMessageId(String senderId, String clientMessageId);
    
    /**
     * 根据消息ID查询
     */
    PrivateMessage findByIdAndConversationId(String id, String conversationId);

    List<PrivateMessage> findByConversationIdAndIdIn(String conversationId, Collection<String> ids);
    
    /**
     * 查询用户发送的消息
     */
    Page<PrivateMessage> findBySenderIdOrderByTimestampDesc(String senderId, Pageable pageable);
    
    /**
     * 查询用户接收的消息
     */
    Page<PrivateMessage> findByReceiverIdOrderByTimestampDesc(String receiverId, Pageable pageable);
    
    /**
     * 查询序列号大于指定值的消息，用于增量同步
     */
    @Query("{ 'conversationId': ?0, '$expr': { '$gt': [ { '$toDecimal': '$seqId' }, { '$toDecimal': ?1 } ] } }")
    List<PrivateMessage> findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(String conversationId, String seqId);

    @Query(value = "{ 'conversationId': ?0, '$expr': { '$gt': [ { '$toDecimal': '$seqId' }, { '$toDecimal': ?1 } ] } }", count = true)
    long countByConversationIdAndSeqIdGreaterThan(String conversationId, String seqId);

    @Query(value = "{ 'conversationId': ?0, 'receiverId': ?1, '$expr': { '$gt': [ { '$toDecimal': '$seqId' }, { '$toDecimal': ?2 } ] } }", count = true)
    long countUnreadByConversationIdAndReceiverIdAndSeqIdGreaterThan(String conversationId, String receiverId, String seqId);

    @Aggregation(pipeline = {
            "{ '$match': { 'conversationId': ?0, '$expr': { '$lt': [ { '$toDecimal': '$seqId' }, { '$toDecimal': ?1 } ] } } }",
            "{ '$addFields': { '__numericSeqId': { '$toDecimal': '$seqId' } } }",
            "{ '$sort': { '__numericSeqId': -1 } }",
            "{ '$project': { '__numericSeqId': 0 } }"
    })
    List<PrivateMessage> findMessagesBeforeSeqId(String conversationId, String seqId, Pageable pageable);

    @Aggregation(pipeline = {
            "{ '$match': { 'conversationId': ?0, '$expr': { '$gt': [ { '$toDecimal': '$seqId' }, { '$toDecimal': ?1 } ] } } }",
            "{ '$addFields': { '__numericSeqId': { '$toDecimal': '$seqId' } } }",
            "{ '$sort': { '__numericSeqId': 1 } }",
            "{ '$project': { '__numericSeqId': 0 } }"
    })
    List<PrivateMessage> findMessagesAfterSeqId(String conversationId, String seqId, Pageable pageable);

    PrivateMessage findByConversationIdAndSeqId(String conversationId, String seqId);

    PrivateMessage findTopByConversationIdOrderBySeqIdDesc(String conversationId);
}
