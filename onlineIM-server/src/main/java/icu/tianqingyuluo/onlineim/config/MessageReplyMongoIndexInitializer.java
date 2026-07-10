package icu.tianqingyuluo.onlineim.config;

import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class MessageReplyMongoIndexInitializer implements InitializingBean {
    private static final String INDEX_NAME = "idx_reply_target_message_id";

    private final MongoTemplate mongoTemplate;

    public MessageReplyMongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        Index replyTargetIndex = new Index()
                .on("replyTo.messageId", Sort.Direction.ASC)
                .named(INDEX_NAME)
                .sparse();
        mongoTemplate.indexOps(PrivateMessage.class).ensureIndex(replyTargetIndex);
        mongoTemplate.indexOps(GroupMessage.class).ensureIndex(replyTargetIndex);
    }
}
