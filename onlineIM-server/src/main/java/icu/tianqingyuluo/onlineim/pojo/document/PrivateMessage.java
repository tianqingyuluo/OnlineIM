package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

/**
 * 单聊消息文档类 (MongoDB)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "private_messages")
public class PrivateMessage {
    @Id
    private String id;             // 消息ID，格式为：msg_+UUID
    private String conversationId; // 会话ID，格式为：conv_+UUID
    private String senderId;       // 发送者ID，格式为：usr_+UUID
    private String receiverId;     // 接收者ID，格式为：usr_+UUID
    private String messageType;    // 消息类型：text, image, voice, file, etc.
    private String content;        // 文本内容或媒体文件引用
    private Integer status;        // 消息状态：0-发送中，1-已送达，2-已读，3-发送失败，4-已撤回
    private String clientMsgId;    // 客户端消息ID，用于消息去重
    private Long seqId;            // 序列号，用于消息排序和增量同步
    private Date timestamp;        // 消息时间戳
    private Date createdAt;        // 创建时间
    private Date updatedAt;        // 更新时间
    private Map<String, Object> ext; // 扩展字段，用于存储自定义属性
} 