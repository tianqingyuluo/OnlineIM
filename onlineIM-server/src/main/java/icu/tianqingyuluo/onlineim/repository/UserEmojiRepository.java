package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.UserEmoji;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserEmojiRepository extends MongoRepository<UserEmoji, String> {

    List<UserEmoji> findByOwnerIdOrderBySortOrderAscCreatedAtDesc(String ownerId);

    long countByOwnerId(String ownerId);

    UserEmoji findByIdAndOwnerId(String id, String ownerId);

    UserEmoji findFirstByOwnerIdAndContentHash(String ownerId, String contentHash);
}
