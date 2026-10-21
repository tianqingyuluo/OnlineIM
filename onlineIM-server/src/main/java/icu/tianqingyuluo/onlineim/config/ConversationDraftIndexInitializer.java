package icu.tianqingyuluo.onlineim.config;

import icu.tianqingyuluo.onlineim.pojo.document.ConversationDraft;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * 会话列表要一次性取出当前用户的全部草稿摘要，按 userId 查询。
 */
@Component
public class ConversationDraftIndexInitializer implements InitializingBean {

    static final String USER_INDEX = "idx_draft_user";

    private final MongoTemplate mongoTemplate;

    public ConversationDraftIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        mongoTemplate.indexOps(ConversationDraft.class).ensureIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .named(USER_INDEX));
    }
}
