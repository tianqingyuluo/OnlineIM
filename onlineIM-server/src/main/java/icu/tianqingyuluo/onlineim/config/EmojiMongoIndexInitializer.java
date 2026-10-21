package icu.tianqingyuluo.onlineim.config;

import icu.tianqingyuluo.onlineim.pojo.document.UserEmoji;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/** 表情列表按用户 + 排序读取；同一用户同一张图（内容哈希）只能收藏一次 */
@Component
public class EmojiMongoIndexInitializer implements InitializingBean {
    private final MongoTemplate mongoTemplate;

    public EmojiMongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        mongoTemplate.indexOps(UserEmoji.class).ensureIndex(new Index()
                .on("ownerId", Sort.Direction.ASC)
                .on("sortOrder", Sort.Direction.ASC)
                .named("idx_owner_sort"));
        mongoTemplate.indexOps(UserEmoji.class).ensureIndex(new Index()
                .on("ownerId", Sort.Direction.ASC)
                .on("contentHash", Sort.Direction.ASC)
                .named("uniq_owner_hash")
                .unique());
    }
}
