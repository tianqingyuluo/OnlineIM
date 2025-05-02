package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
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
    PrivateMessage findByClientMsgId(String clientMsgId);
    
    /**
     * 根据消息ID查询
     */
    PrivateMessage findByIdAndConversationId(String id, String conversationId);
    
    /**
     * 查询用户发送的消息
     */
    Page<PrivateMessage> findBySenderIdOrderByTimestampDesc(String senderId, Pageable pageable);
    
    /**
     * 查询用户接收的消息
     */
    Page<PrivateMessage> findByReceiverIdOrderByTimestampDesc(String receiverId, Pageable pageable);
} 