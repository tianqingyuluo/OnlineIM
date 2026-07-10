package icu.tianqingyuluo.onlineim.config;

import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageReplyMongoIndexInitializerTest {

    @Test
    void ensuresReplyTargetIndexesForBothMessageCollections() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations privateIndexes = mock(IndexOperations.class);
        IndexOperations groupIndexes = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(PrivateMessage.class)).thenReturn(privateIndexes);
        when(mongoTemplate.indexOps(GroupMessage.class)).thenReturn(groupIndexes);

        new MessageReplyMongoIndexInitializer(mongoTemplate).afterPropertiesSet();

        verify(privateIndexes).ensureIndex(any(Index.class));
        verify(groupIndexes).ensureIndex(any(Index.class));
    }
}
