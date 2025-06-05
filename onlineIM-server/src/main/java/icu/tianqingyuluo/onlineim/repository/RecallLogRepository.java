package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.RecallLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息撤回日志MongoDB仓库接口
 */
@Repository
public interface RecallLogRepository extends MongoRepository<RecallLog, String> {

    /**
     * 查询指定会话中序列号大于指定值的所有撤回记录
     * @param conversationId 会话ID
     * @param seqId 序列号
     * @return 撤回记录列表
     */
    List<RecallLog> findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(String conversationId, String seqId);
}
