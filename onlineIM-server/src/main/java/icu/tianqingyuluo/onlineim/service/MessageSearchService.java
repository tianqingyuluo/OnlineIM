package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.mapper.UserFriendMapper;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageSearchResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserResponse;
import icu.tianqingyuluo.onlineim.util.SearchSnippet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 消息搜索（GET /api/v1/messages/search）。
 *
 * <p>范围：只搜文本消息（message_type = text）的当前内容（编辑过的按编辑后的内容匹配），排除已撤回的消息。
 * 全局搜索覆盖当前用户“会话列表里能看到”的会话：自己的私聊会话记录（未删除 / 隐藏）与仍是成员且未删除的群；
 * 指定 conversation_id 时只搜该会话，权限与历史记录接口一致（私聊要有自己的会话记录，群聊要是成员），否则 403。
 * 群成员可以搜到入群前的消息（与历史记录接口一致）。</p>
 *
 * <p>检索：关键字去首尾空白后按字面量转义为正则，忽略大小写的子串匹配（中文无需分词）；
 * 先用 (conversationId|groupId, messageType, timestamp) 索引圈定会话与类型，再在这些消息上做正则过滤。
 * 结果按时间倒序，游标分页（游标 = 最后一条的 时间毫秒:消息ID）。</p>
 */
@Slf4j
@Service
public class MessageSearchService {
    public static final int MIN_KEYWORD_LENGTH = 1;
    public static final int MAX_KEYWORD_LENGTH = 50;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    static final String TEXT = "text";
    static final int RECALLED = 3;
    private static final Pattern REGEX_META = Pattern.compile("[\\\\^$.|?*+()\\[\\]{}\\-/]");

    private final MongoTemplate mongoTemplate;
    private final MessageService messageService;
    private final GroupService groupService;
    private final GroupMemberService groupMemberService;
    private final UserService userService;
    private final UserFriendMapper userFriendMapper;

    public MessageSearchService(MongoTemplate mongoTemplate, MessageService messageService,
                                GroupService groupService, GroupMemberService groupMemberService,
                                UserService userService, UserFriendMapper userFriendMapper) {
        this.mongoTemplate = mongoTemplate;
        this.messageService = messageService;
        this.groupService = groupService;
        this.groupMemberService = groupMemberService;
        this.userService = userService;
        this.userFriendMapper = userFriendMapper;
    }

    public MessageSearchResponse search(String userId, String rawKeyword, String conversationId,
                                        String cursor, Integer size) {
        String keyword = normalizeKeyword(rawKeyword);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        Cursor after = Cursor.parse(cursor);

        List<String> privateIds = new ArrayList<>();
        List<String> groupIds = new ArrayList<>();
        String scope = conversationId == null || conversationId.isBlank() ? null : conversationId.trim();
        if (scope != null) {
            if (!canRead(scope, userId)) {
                throw new ForbiddenException("无权搜索该会话");
            }
            (scope.startsWith("grp_") ? groupIds : privateIds).add(scope);
        } else {
            collectVisibleConversations(userId, privateIds, groupIds);
        }

        String regex = escapeRegex(keyword);
        List<Hit> hits = new ArrayList<>();
        if (!privateIds.isEmpty()) {
            for (PrivateMessage message : mongoTemplate.find(
                    pageQuery("conversationId", privateIds, regex, after, pageSize + 1), PrivateMessage.class)) {
                hits.add(new Hit(message.getTimestamp(), message.getId(), message, null));
            }
        }
        if (!groupIds.isEmpty()) {
            for (GroupMessage message : mongoTemplate.find(
                    pageQuery("groupId", groupIds, regex, after, pageSize + 1), GroupMessage.class)) {
                hits.add(new Hit(message.getTimestamp(), message.getId(), null, message));
            }
        }
        hits.sort(Hit.NEWEST_FIRST);
        boolean hasMore = hits.size() > pageSize;
        List<Hit> page = hits.subList(0, Math.min(pageSize, hits.size()));

        Map<String, MessageSearchResponse.ConversationInfo> conversations = new HashMap<>();
        List<MessageSearchResponse.Item> items = new ArrayList<>(page.size());
        for (Hit hit : page) {
            String content = hit.isGroup() ? hit.group().getContent() : hit.privateMessage().getContent();
            String convId = hit.isGroup() ? hit.group().getGroupId() : hit.privateMessage().getConversationId();
            SearchSnippet.Result snippet = SearchSnippet.build(content, keyword);
            items.add(MessageSearchResponse.Item.builder()
                    .message(hit.isGroup()
                            ? messageService.convertGroupMessageToResponse(hit.group())
                            : messageService.convertPrivateMessageToResponse(hit.privateMessage()))
                    .conversation(conversations.computeIfAbsent(convId, id -> conversationInfo(id, userId)))
                    .snippet(snippet.snippet())
                    .highlights(snippet.highlights().stream()
                            .map(h -> new MessageSearchResponse.Highlight(h.start(), h.length()))
                            .toList())
                    .build());
        }
        String nextCursor = hasMore && !page.isEmpty() ? Cursor.of(page.getLast()) : null;
        return MessageSearchResponse.builder()
                .keyword(keyword)
                .conversationId(scope)
                .results(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    static String normalizeKeyword(String rawKeyword) {
        String keyword = rawKeyword == null ? "" : rawKeyword.strip();
        int length = keyword.codePointCount(0, keyword.length());
        if (length < MIN_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("请输入搜索关键字");
        }
        if (length > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("搜索关键字不能超过" + MAX_KEYWORD_LENGTH + "个字");
        }
        return keyword;
    }

    /** 把关键字转成字面量正则（MongoDB 使用 PCRE，逐个转义元字符） */
    static String escapeRegex(String keyword) {
        return REGEX_META.matcher(keyword).replaceAll(match -> "\\\\" + java.util.regex.Matcher.quoteReplacement(match.group()));
    }

    private Query pageQuery(String conversationField, Collection<String> ids, String regex, Cursor after, int limit) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where(conversationField).in(ids),
                Criteria.where("messageType").is(TEXT),
                Criteria.where("status").ne(RECALLED),
                Criteria.where("content").regex(regex, "i"),
                after == null ? new Criteria() : new Criteria().orOperator(
                        Criteria.where("timestamp").lt(after.timestamp()),
                        new Criteria().andOperator(
                                Criteria.where("timestamp").is(after.timestamp()),
                                Criteria.where("_id").lt(after.messageId()))));
        return new Query(criteria)
                .with(Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("_id")))
                .limit(limit);
    }

    private boolean canRead(String conversationId, String userId) {
        if (conversationId.startsWith("grp_")) {
            return groupMemberService.isGroupMember(conversationId, userId);
        }
        return mongoTemplate.exists(Query.query(Criteria.where("conversationId").is(conversationId)
                .and("userId").is(userId)), Conversation.class);
    }

    /** 会话列表里能看到的会话：未删除（status != 0）的私聊记录；仍是成员、且自己没有删除该群会话的群 */
    private void collectVisibleConversations(String userId, List<String> privateIds, List<String> groupIds) {
        Set<String> hiddenGroups = new LinkedHashSet<>();
        Query records = Query.query(Criteria.where("userId").is(userId));
        records.fields().include("conversationId").include("conversationType").include("status");
        for (Conversation record : mongoTemplate.find(records, Conversation.class)) {
            String id = record.getConversationId();
            if (id == null) continue;
            boolean hidden = Objects.equals(record.getStatus(), 0);
            if (id.startsWith("grp_")) {
                if (hidden) hiddenGroups.add(id);
            } else if (!hidden) {
                privateIds.add(id);
            }
        }
        List<GroupResponse> joined = groupService.getJoinedGroupsByUserID(userId);
        if (joined != null) {
            for (GroupResponse group : joined) {
                if (group.getGroupID() != null && !hiddenGroups.contains(group.getGroupID())) {
                    groupIds.add(group.getGroupID());
                }
            }
        }
    }

    private MessageSearchResponse.ConversationInfo conversationInfo(String conversationId, String userId) {
        String name = conversationId;
        String avatar = "";
        String type = conversationId.startsWith("grp_") ? "group" : "private";
        try {
            if ("group".equals(type)) {
                GroupBriefResponse group = groupService.getGroupByID(conversationId);
                if (group != null) {
                    name = group.getName();
                    avatar = group.getAvatar() == null ? "" : group.getAvatar();
                }
            } else {
                String otherUserId = otherParticipant(conversationId, userId);
                String remark = otherUserId == null ? null : userFriendMapper.getRemarkByUserIdAndFriendId(userId, otherUserId);
                UserResponse other = otherUserId == null ? null : userService.getUserInfoByUserID(otherUserId);
                if (remark != null && !remark.isBlank()) {
                    name = remark;
                } else if (other != null && other.getNickname() != null) {
                    name = other.getNickname();
                } else if (otherUserId != null) {
                    name = otherUserId;
                }
                if (other != null && other.getAvatarUrl() != null) {
                    avatar = other.getAvatarUrl();
                }
            }
        } catch (RuntimeException e) {
            log.warn("搜索结果会话信息读取失败 {}: {}", conversationId, e.getMessage());
        }
        return MessageSearchResponse.ConversationInfo.builder()
                .conversationId(conversationId)
                .type(type)
                .name(name)
                .avatarUrl(avatar)
                .build();
    }

    /** 私聊会话 ID 为 conv_ + 两个 usr_ 用户 ID 按字典序拼接 */
    static String otherParticipant(String conversationId, String userId) {
        if (conversationId == null || !conversationId.startsWith("conv_")) return null;
        String[] parts = conversationId.substring("conv_".length()).split("usr_");
        if (parts.length != 3) return null;
        String first = "usr_" + parts[1];
        String second = "usr_" + parts[2];
        return first.equals(userId) ? second : first;
    }

    private record Hit(Date timestamp, String id, PrivateMessage privateMessage, GroupMessage group) {
        static final Comparator<Hit> NEWEST_FIRST = Comparator
                .comparing(Hit::timestamp, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Hit::id, Comparator.reverseOrder());

        boolean isGroup() {
            return group != null;
        }
    }

    record Cursor(Date timestamp, String messageId) {
        static Cursor parse(String value) {
            if (value == null || value.isBlank()) return null;
            int separator = value.indexOf(':');
            if (separator <= 0 || separator == value.length() - 1) {
                throw new IllegalArgumentException("无效的分页游标");
            }
            try {
                return new Cursor(new Date(Long.parseLong(value.substring(0, separator))), value.substring(separator + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("无效的分页游标");
            }
        }

        static String of(Hit hit) {
            return (hit.timestamp() == null ? 0 : hit.timestamp().getTime()) + ":" + hit.id();
        }
    }
}
