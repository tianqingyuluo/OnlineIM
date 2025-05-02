package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
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
    GroupMessage findByClientMsgId(String clientMsgId);
    
    /**
     * 根据消息ID查询
     */
    GroupMessage findByIdAndGroupId(String id, String groupId);
    
    /**
     * 查询用户在群里发送的消息
     */
    Page<GroupMessage> findBySenderIdAndGroupIdOrderByTimestampDesc(String senderId, String groupId, Pageable pageable);
    
    /**
     * 查询@指定用户的消息
     */
    @Query("{'groupId': ?0, 'atUsers': ?1}")
    List<GroupMessage> findByGroupIdAndAtUsers(String groupId, String userId, Pageable pageable);
} 