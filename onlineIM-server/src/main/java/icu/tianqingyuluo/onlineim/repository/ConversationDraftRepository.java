package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.ConversationDraft;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationDraftRepository extends MongoRepository<ConversationDraft, String> {

    List<ConversationDraft> findByUserId(String userId);
}
