package icu.tianqingyuluo.onlineim.config;

import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * 消息搜索索引：先按会话与消息类型圈定范围、按 (时间, 消息ID) 倒序取页（与分页游标一致，不需要内存排序），再对 content 做正则过滤。
 * 中文没有分词，MongoDB text index 对中文按整段切词效果差，因此 v1 使用“范围索引 + 字面量正则”。
 */
@Component
public class MessageSearchMongoIndexInitializer implements InitializingBean {
    static final String PRIVATE_INDEX = "idx_search_conversation_type_time_id";
    static final String GROUP_INDEX = "idx_search_group_type_time_id";

    private final MongoTemplate mongoTemplate;

    public MessageSearchMongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        mongoTemplate.indexOps(PrivateMessage.class).ensureIndex(new Index()
                .on("conversationId", Sort.Direction.ASC)
                .on("messageType", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .named(PRIVATE_INDEX));
        mongoTemplate.indexOps(GroupMessage.class).ensureIndex(new Index()
                .on("groupId", Sort.Direction.ASC)
                .on("messageType", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .named(GROUP_INDEX));
    }
}
