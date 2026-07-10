package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.ConversationReadState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationReadStateRepository extends MongoRepository<ConversationReadState, String> {

    Optional<ConversationReadState> findByConversationIdAndUserId(String conversationId, String userId);

    List<ConversationReadState> findByConversationId(String conversationId);
}
