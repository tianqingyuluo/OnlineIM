package icu.tianqingyuluo.onlineim.repository;

import icu.tianqingyuluo.onlineim.pojo.document.ChatUpload;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatUploadRepository extends MongoRepository<ChatUpload, String> {

    ChatUpload findByObjectKey(String objectKey);

    ChatUpload findByThumbnailKey(String thumbnailKey);
}
