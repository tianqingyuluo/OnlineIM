package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
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
 * 群聊消息MongoDB仓库接口
 */
@Repository
public interface GroupMessageRepository extends MongoRepository<GroupMessage, String> {

    /**
     * 根据群组ID查询消息历史
     */
    Page<GroupMessage> findByGroupIdOrderByTimestampDesc(String groupId, Pageable pageable);
    
    /**
     * 查询指定消息ID之前的历史消息
     */
    @Query("{'groupId': ?0, 'timestamp': {$lt: ?1}}")
    List<GroupMessage> findMessagesBeforeTimestamp(String groupId, Date timestamp, Pageable pageable);
    
    /**
     * 查询指定消息ID之后的新消息
     */
    @Query("{'groupId': ?0, 'timestamp': {$gt: ?1}}")
    List<GroupMessage> findMessagesAfterTimestamp(String groupId, Date timestamp, Pageable pageable);
    
    /**
     * 根据客户端消息ID查询消息
     */
    GroupMessage findByClientMessageId(String clientMsgId);

    /**
     * 按发送者和客户端消息ID查询，避免不同发送者使用相同客户端ID时误去重。
     */
    GroupMessage findBySenderIdAndClientMessageId(String senderId, String clientMessageId);
    
    /**
     * 根据消息ID查询
     */
    GroupMessage findByIdAndGroupId(String id, String groupId);

    List<GroupMessage> findByGroupIdAndIdIn(String groupId, Collection<String> ids);
    
    /**
     * 查询用户在群里发送的消息
     */
    Page<GroupMessage> findBySenderIdAndGroupIdOrderByTimestampDesc(String senderId, String groupId, Pageable pageable);
    
    /**
     * 查询@指定用户的消息
     */
    @Query("{'groupId': ?0, 'atUsers': ?1}")
    List<GroupMessage> findByGroupIdAndAtUsers(String groupId, String userId, Pageable pageable);
    
    /**
     * 查询序列号大于指定值的消息，用于增量同步
     */
    @Query("{ 'groupId': ?0, '$expr': { '$gt': [ { '$toDecimal': '$seqId' }, { '$toDecimal': ?1 } ] } }")
    List<GroupMessage> findByGroupIdAndSeqIdGreaterThanOrderBySeqIdAsc(String groupId, String seqId);

    @Query(value = "{ 'groupId': ?0, '$expr': { '$gt': [ { '$toDecimal': '$seqId' }, { '$toDecimal': ?1 } ] } }", count = true)
    long countByGroupIdAndSeqIdGreaterThan(String groupId, String seqId);

    @Query(value = "{ 'groupId': ?0, 'senderId': { '$ne': ?1 }, '$expr': { '$gt': [ { '$toDecimal': '$seqId' }, { '$toDecimal': ?2 } ] } }", count = true)
    long countUnreadByGroupIdAndUserIdAndSeqIdGreaterThan(String groupId, String userId, String seqId);

    @Aggregation(pipeline = {
            "{ '$match': { 'groupId': ?0, '$expr': { '$lt': [ { '$toDecimal': '$seqId' }, { '$toDecimal': ?1 } ] } } }",
            "{ '$addFields': { '__numericSeqId': { '$toDecimal': '$seqId' } } }",
            "{ '$sort': { '__numericSeqId': -1 } }",
            "{ '$project': { '__numericSeqId': 0 } }"
    })
    List<GroupMessage> findMessagesBeforeSeqId(String groupId, String seqId, Pageable pageable);

    @Aggregation(pipeline = {
            "{ '$match': { 'groupId': ?0, '$expr': { '$gt': [ { '$toDecimal': '$seqId' }, { '$toDecimal': ?1 } ] } } }",
            "{ '$addFields': { '__numericSeqId': { '$toDecimal': '$seqId' } } }",
            "{ '$sort': { '__numericSeqId': 1 } }",
            "{ '$project': { '__numericSeqId': 0 } }"
    })
    List<GroupMessage> findMessagesAfterSeqId(String groupId, String seqId, Pageable pageable);

    GroupMessage findByGroupIdAndSeqId(String groupId, String seqId);

    GroupMessage findTopByGroupIdOrderBySeqIdDesc(String groupId);
}
