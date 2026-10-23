package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.ChatUpload;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.document.UserEmoji;
import icu.tianqingyuluo.onlineim.repository.ChatUploadRepository;
import icu.tianqingyuluo.onlineim.repository.UserEmojiRepository;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天对象（图片、文件、语音、表情）的读取授权。
 * 对象键本身不含会话 ID，发送消息时把会话记到上传/表情记录上；
 * 历史消息没有这条记录时，按对象键回查消息内容补一次。
 */
@Service
public class ChatFileAccessService {

    public enum ReadDecision {
        ALLOW,
        DENY,
        ABSENT
    }

    /** 与上传实现一致：chats/{images|files|voices|videos|emojis}/yyyyMM/&lt;32 hex&gt;[_thumb].ext */
    static final Pattern CHAT_OBJECT = Pattern.compile(
            "chats/(?:images|files|voices|videos|emojis)/\\d{6}/[0-9a-f]{32}(?:_thumb)?\\.[a-z0-9]{1,8}");
    private static final Pattern FILE_ID = Pattern.compile("upl_[0-9a-f]{32}");

    private final ChatUploadRepository chatUploadRepository;
    private final UserEmojiRepository userEmojiRepository;
    private final MongoTemplate mongoTemplate;
    private final GroupMemberService groupMemberService;

    public ChatFileAccessService(ChatUploadRepository chatUploadRepository,
                                 UserEmojiRepository userEmojiRepository,
                                 MongoTemplate mongoTemplate,
                                 GroupMemberService groupMemberService) {
        this.chatUploadRepository = chatUploadRepository;
        this.userEmojiRepository = userEmojiRepository;
        this.mongoTemplate = mongoTemplate;
        this.groupMemberService = groupMemberService;
    }

    /**
     * 消息落库前调用：把 content 里出现的 file_id / 对象键记到对应上传或表情的会话列表。
     * 合并转发的聊天记录里嵌套的媒体 URL 也会被扫到。
     */
    public void attach(String content, String conversationId) {
        if (content == null || content.isBlank() || !isConversationId(conversationId)) {
            return;
        }
        Set<String> fileIds = new LinkedHashSet<>();
        Matcher fileMatcher = FILE_ID.matcher(content);
        while (fileMatcher.find()) {
            fileIds.add(fileMatcher.group());
        }
        for (String fileId : fileIds) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(fileId)),
                    new Update().addToSet("conversationIds", conversationId), ChatUpload.class);
        }
        Matcher keyMatcher = CHAT_OBJECT.matcher(content);
        while (keyMatcher.find()) {
            String key = keyMatcher.group();
            if (key.startsWith("chats/emojis/")) {
                mongoTemplate.updateMulti(Query.query(Criteria.where("objectKey").is(key)),
                        new Update().addToSet("conversationIds", conversationId), UserEmoji.class);
            } else if (key.contains("_thumb.")) {
                mongoTemplate.updateMulti(Query.query(Criteria.where("thumbnailKey").is(key)),
                        new Update().addToSet("conversationIds", conversationId), ChatUpload.class);
            } else {
                mongoTemplate.updateMulti(Query.query(Criteria.where("objectKey").is(key)),
                        new Update().addToSet("conversationIds", conversationId), ChatUpload.class);
            }
        }
    }

    /**
     * @return ALLOW 可以读；DENY 文件存在但当前用户不是所属会话的参与者；ABSENT 没有这样的聊天文件
     */
    public ReadDecision decide(String userId, String objectName) {
        if (userId == null || userId.isBlank() || !isTrackableChatObject(objectName)) {
            return ReadDecision.ABSENT;
        }
        ChatUpload upload = chatUploadRepository.findByObjectKey(objectName);
        if (upload == null) {
            upload = chatUploadRepository.findByThumbnailKey(objectName);
        }
        if (upload != null) {
            return decideUpload(userId, upload, objectName);
        }
        List<UserEmoji> emojis = userEmojiRepository.findByObjectKey(objectName);
        if (emojis != null && !emojis.isEmpty()) {
            return decideEmojis(userId, emojis, objectName);
        }
        List<String> discovered = discoverConversations(objectName);
        if (discovered.isEmpty()) {
            return ReadDecision.ABSENT;
        }
        return isParticipant(userId, discovered) ? ReadDecision.ALLOW : ReadDecision.DENY;
    }

    private ReadDecision decideUpload(String userId, ChatUpload upload, String objectName) {
        List<String> bound = upload.getConversationIds() == null ? List.of() : upload.getConversationIds();
        if (isParticipant(userId, bound)) {
            return ReadDecision.ALLOW;
        }
        if (!bound.isEmpty()) {
            return ReadDecision.DENY;
        }
        List<String> discovered = discoverConversations(objectName);
        if (!discovered.isEmpty()) {
            rememberUpload(upload.getId(), discovered);
            return isParticipant(userId, discovered) ? ReadDecision.ALLOW : ReadDecision.DENY;
        }
        // 刚上传、还没发出去：只有上传者能预览
        return userId.equals(upload.getUploaderId()) ? ReadDecision.ALLOW : ReadDecision.DENY;
    }

    private ReadDecision decideEmojis(String userId, List<UserEmoji> emojis, String objectName) {
        boolean owner = emojis.stream().anyMatch(emoji -> userId.equals(emoji.getOwnerId()));
        if (owner) {
            return ReadDecision.ALLOW;
        }
        List<String> bound = new ArrayList<>();
        for (UserEmoji emoji : emojis) {
            if (emoji.getConversationIds() != null) {
                bound.addAll(emoji.getConversationIds());
            }
        }
        if (isParticipant(userId, bound)) {
            return ReadDecision.ALLOW;
        }
        if (!bound.isEmpty()) {
            return ReadDecision.DENY;
        }
        List<String> discovered = discoverConversations(objectName);
        if (discovered.isEmpty()) {
            return ReadDecision.DENY;
        }
        rememberEmoji(objectName, discovered);
        return isParticipant(userId, discovered) ? ReadDecision.ALLOW : ReadDecision.DENY;
    }

    private void rememberUpload(String uploadId, List<String> conversationIds) {
        if (uploadId == null || conversationIds.isEmpty()) {
            return;
        }
        Update update = new Update();
        conversationIds.forEach(id -> update.addToSet("conversationIds", id));
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(uploadId)), update, ChatUpload.class);
    }

    private void rememberEmoji(String objectKey, List<String> conversationIds) {
        Update update = new Update();
        conversationIds.forEach(id -> update.addToSet("conversationIds", id));
        mongoTemplate.updateMulti(Query.query(Criteria.where("objectKey").is(objectKey)), update, UserEmoji.class);
    }

    private List<String> discoverConversations(String objectName) {
        String pattern = literalRegex(objectName);
        Set<String> ids = new LinkedHashSet<>();
        Query privateQuery = Query.query(Criteria.where("content").regex(pattern));
        privateQuery.fields().include("conversationId");
        for (PrivateMessage message : mongoTemplate.find(privateQuery, PrivateMessage.class)) {
            if (isConversationId(message.getConversationId())) {
                ids.add(message.getConversationId());
            }
        }
        Query groupQuery = Query.query(Criteria.where("content").regex(pattern));
        groupQuery.fields().include("groupId");
        for (GroupMessage message : mongoTemplate.find(groupQuery, GroupMessage.class)) {
            if (isConversationId(message.getGroupId())) {
                ids.add(message.getGroupId());
            }
        }
        return List.copyOf(ids);
    }

    private boolean isParticipant(String userId, Collection<String> conversationIds) {
        if (conversationIds == null) {
            return false;
        }
        for (String conversationId : conversationIds) {
            if (conversationId == null || conversationId.isBlank()) {
                continue;
            }
            if (conversationId.startsWith("grp_")) {
                if (groupMemberService.isGroupMember(conversationId, userId)) {
                    return true;
                }
            } else if (ConversationIdUtil.otherParticipant(conversationId, userId) != null) {
                return true;
            }
        }
        return false;
    }

    static boolean isTrackableChatObject(String objectName) {
        return objectName != null && CHAT_OBJECT.matcher(objectName).matches();
    }

    private static boolean isConversationId(String conversationId) {
        return conversationId != null
                && (conversationId.startsWith("conv_") || conversationId.startsWith("grp_"));
    }

    /** Mongo 正则没有 Java 的 \\Q\\E，这里按字面量转义。 */
    static String literalRegex(String value) {
        StringBuilder out = new StringBuilder(value.length() * 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ("\\.^$|?*+()[]{}".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
