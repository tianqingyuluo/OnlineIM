package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 聊天上传记录。发送图片/文件消息时只引用 {@link #id}，消息里的 URL、大小、尺寸等元数据由服务端从这里取，
 * 客户端无法伪造外部链接或别人的文件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_uploads")
public class ChatUpload {
    @Id
    private String id;            // upl_ + 32 位随机十六进制
    private String uploaderId;
    private String kind;          // image / file
    private String objectKey;     // chats/images/yyyyMM/<random>.png
    private String url;
    private String thumbnailKey;
    private String thumbnailUrl;
    private String name;          // 清洗后的原始文件名
    private Long size;
    private String mime;
    private Integer width;
    private Integer height;
    private Integer thumbnailWidth;
    private Integer thumbnailHeight;
    private Long thumbnailSize;
    private Date createdAt;
}
