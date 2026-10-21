package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 用户收藏的自定义表情。发送表情消息时只引用 {@link #id}，消息里的 URL、尺寸等由服务端从这里取。
 * 删除表情只删除收藏记录，对象存储里的文件保留，已发送的历史消息仍可显示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_emojis")
public class UserEmoji {
    @Id
    private String id;            // emo_ + 32 位随机十六进制
    private String ownerId;
    private String objectKey;     // chats/emojis/yyyyMM/<random>.gif
    private String url;
    private String name;
    private String mime;          // image/png、image/jpeg、image/gif
    private Long size;
    private Integer width;
    private Integer height;
    private Boolean animated;
    private Integer frameCount;
    private String contentHash;   // SHA-256，同一用户内去重
    private Long sortOrder;       // 升序排列；新表情取负的创建毫秒数，排在最前
    private String source;        // upload / message
    private String sourceMessageId;
    private Date createdAt;
}
