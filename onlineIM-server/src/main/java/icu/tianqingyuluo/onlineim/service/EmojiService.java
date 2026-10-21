package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.EmojiProperties;
import icu.tianqingyuluo.onlineim.exception.ChatMediaException;
import icu.tianqingyuluo.onlineim.pojo.document.ChatUpload;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.document.UserEmoji;
import icu.tianqingyuluo.onlineim.repository.ChatUploadRepository;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.repository.UserEmojiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义表情（每人上限 {@code im.emoji.max-count}，默认 100）：上传 / 从聊天消息收藏 / 列表 / 删除 / 排序，
 * 以及发送表情消息时把 emoji_id 解析成服务端权威的内容。
 *
 * <p>对象键：{@code chats/emojis/yyyyMM/<128 位随机十六进制>.png|jpg|gif}，原图原样保存（GIF 动画保留），
 * 浏览器经 {@code /onlineim/**} 代理读取。删除表情只删收藏记录，不删对象，已发送的历史消息仍可显示。</p>
 */
@Slf4j
@Service
public class EmojiService {

    public static final String TYPE_EMOJI = "emoji";
    public static final String TYPE_STICKER = "sticker";
    static final Pattern EMOJI_ID = Pattern.compile("emo_[0-9a-f]{32}");
    static final Pattern EMOJI_OBJECT_KEY = Pattern.compile("chats/emojis/\\d{6}/[0-9a-f]{32}\\.(png|jpg|gif)");
    private static final Pattern EMOJI_OBJECT_IN_URL = Pattern.compile("(chats/emojis/\\d{6}/[0-9a-f]{32}\\.(?:png|jpg|gif))$");

    private final FileStorageService storage;
    private final UserEmojiRepository repository;
    private final ChatUploadRepository uploadRepository;
    private final PrivateMessageRepository privateMessageRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final ConversationRepository conversationRepository;
    private final GroupMemberService groupMemberService;
    private final EmojiProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public EmojiService(FileStorageService storage, UserEmojiRepository repository,
                        ChatUploadRepository uploadRepository,
                        PrivateMessageRepository privateMessageRepository,
                        GroupMessageRepository groupMessageRepository,
                        ConversationRepository conversationRepository,
                        GroupMemberService groupMemberService,
                        EmojiProperties properties, ObjectMapper objectMapper) {
        this.storage = storage;
        this.repository = repository;
        this.uploadRepository = uploadRepository;
        this.privateMessageRepository = privateMessageRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.conversationRepository = conversationRepository;
        this.groupMemberService = groupMemberService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public static boolean isEmojiMessage(String messageType) {
        return TYPE_EMOJI.equals(messageType) || TYPE_STICKER.equals(messageType);
    }

    public List<UserEmoji> list(String userId) {
        return repository.findByOwnerIdOrderBySortOrderAscCreatedAtDesc(userId);
    }

    /** 列表接口附带的限制信息，客户端据此做上传前校验与“已用/上限”展示 */
    public Map<String, Object> limits() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("max_count", properties.getMaxCount());
        limits.put("max_size", properties.getMaxSize().toBytes());
        limits.put("max_dimension", properties.getMaxDimension());
        limits.put("formats", properties.getFormats());
        return limits;
    }

    public UserEmoji upload(String userId, MultipartFile file, String name) {
        if (file == null || file.isEmpty()) {
            throw ChatMediaException.badRequest("文件不能为空");
        }
        long limit = Math.max(properties.getMaxSize().toBytes(), properties.getMaxSourceSize().toBytes());
        if (file.getSize() > limit) {
            throw ChatMediaException.tooLarge("表情图片不能超过 " + formatLimit(limit));
        }
        try {
            String displayName = name != null && !name.isBlank() ? name : stripExtension(file.getOriginalFilename());
            return add(userId, file.getBytes(), displayName, "upload", null);
        } catch (IOException e) {
            throw new IllegalStateException("表情上传失败，请稍后重试", e);
        }
    }

    /** 把聊天中可见的图片 / 表情消息收藏为自己的表情（复制一份对象，与原消息互不影响） */
    public UserEmoji saveFromMessage(String userId, String conversationId, String messageId) {
        if (isBlank(conversationId) || isBlank(messageId)) {
            throw ChatMediaException.badRequest("conversation_id 和 message_id 不能为空");
        }
        boolean group = conversationId.startsWith("grp_");
        boolean allowed = group
                ? groupMemberService.isGroupMember(conversationId, userId)
                : conversationRepository.findByConversationIdAndUserId(conversationId, userId) != null;
        if (!allowed) {
            throw notFound("消息不存在或已撤回");
        }
        String type;
        String content;
        Integer status;
        if (group) {
            GroupMessage message = groupMessageRepository.findByIdAndGroupId(messageId, conversationId);
            if (message == null) {
                throw notFound("消息不存在或已撤回");
            }
            type = message.getMessageType();
            content = message.getContent();
            status = message.getStatus();
        } else {
            PrivateMessage message = privateMessageRepository.findByIdAndConversationId(messageId, conversationId);
            if (message == null) {
                throw notFound("消息不存在或已撤回");
            }
            type = message.getMessageType();
            content = message.getContent();
            status = message.getStatus();
        }
        if (status != null && status == 3) {
            throw notFound("消息不存在或已撤回");
        }

        String objectKey;
        String name;
        if ("image".equals(type)) {
            ChatUpload upload = uploadRepository.findById(textField(content, "file_id")).orElse(null);
            if (upload == null || upload.getObjectKey() == null) {
                throw notFound("图片已不存在");
            }
            objectKey = upload.getObjectKey();
            name = stripExtension(upload.getName());
        } else if (isEmojiMessage(type)) {
            objectKey = emojiObjectKeyOf(textField(content, "url"));
            if (objectKey == null) {
                throw notFound("表情已不存在");
            }
            name = textField(content, "name");
        } else {
            throw ChatMediaException.badRequest("只能把图片或表情消息添加到表情");
        }
        return add(userId, readObject(objectKey), name, "message", messageId);
    }

    public void delete(String userId, String emojiId) {
        UserEmoji emoji = isBlank(emojiId) ? null : repository.findByIdAndOwnerId(emojiId, userId);
        if (emoji == null) {
            throw notFound("表情不存在");
        }
        repository.delete(emoji);
    }

    /** 按客户端给出的完整顺序重排；必须恰好包含自己的全部表情各一次 */
    public List<UserEmoji> reorder(String userId, List<String> emojiIds) {
        List<UserEmoji> current = list(userId);
        if (emojiIds == null || emojiIds.size() != current.size() || new HashSet<>(emojiIds).size() != emojiIds.size()) {
            throw ChatMediaException.badRequest("排序列表必须包含全部表情且不能重复");
        }
        Map<String, UserEmoji> byId = new HashMap<>();
        current.forEach(emoji -> byId.put(emoji.getId(), emoji));
        List<UserEmoji> ordered = new ArrayList<>(emojiIds.size());
        for (int i = 0; i < emojiIds.size(); i++) {
            UserEmoji emoji = byId.get(emojiIds.get(i));
            if (emoji == null) {
                throw ChatMediaException.badRequest("排序列表包含不存在的表情");
            }
            emoji.setSortOrder((long) i);
            ordered.add(emoji);
        }
        repository.saveAll(ordered);
        return ordered;
    }

    /**
     * 发送表情消息：content 只需携带 emoji_id（或含 emoji_id 的 JSON），只能发送自己收藏中的表情；
     * URL、尺寸、是否动图等由服务端填充。
     */
    public ChatMediaService.ResolvedMedia resolveForMessage(String senderId, String content) {
        String emojiId = extractEmojiId(content);
        if (emojiId == null) {
            throw ChatMediaException.badRequest("表情消息缺少 emoji_id");
        }
        UserEmoji emoji = senderId == null ? null : repository.findByIdAndOwnerId(emojiId, senderId);
        if (emoji == null) {
            throw ChatMediaException.badRequest("表情不存在或不属于当前用户");
        }
        Map<String, Object> media = toMessageMap(emoji);
        try {
            return new ChatMediaService.ResolvedMedia(objectMapper.writeValueAsString(media), new LinkedHashMap<>(media));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("表情信息序列化失败", e);
        }
    }

    /** REST 接口返回的表情字段（snake_case） */
    public Map<String, Object> toResponseMap(UserEmoji emoji) {
        Map<String, Object> map = toMessageMap(emoji);
        map.put("sort_order", emoji.getSortOrder());
        map.put("created_at", emoji.getCreatedAt());
        return map;
    }

    Map<String, Object> toMessageMap(UserEmoji emoji) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("emoji_id", emoji.getId());
        map.put("type", TYPE_EMOJI);
        map.put("url", emoji.getUrl());
        map.put("name", emoji.getName());
        map.put("mime", emoji.getMime());
        map.put("size", emoji.getSize());
        map.put("width", emoji.getWidth());
        map.put("height", emoji.getHeight());
        map.put("animated", Boolean.TRUE.equals(emoji.getAnimated()));
        return map;
    }

    UserEmoji add(String userId, byte[] bytes, String name, String source, String sourceMessageId) {
        Prepared image = prepare(bytes);
        String hash = sha256(image.bytes());
        if (repository.findFirstByOwnerIdAndContentHash(userId, hash) != null) {
            throw conflict("这个表情已经在你的表情里了");
        }
        if (repository.countByOwnerId(userId) >= properties.getMaxCount()) {
            throw limitReached();
        }

        String objectKey = objectBase() + "." + ("jpeg".equals(image.format()) ? "jpg" : image.format());
        String mime = "image/" + image.format();
        String url;
        try {
            url = storage.uploadObject(objectKey, new ByteArrayInputStream(image.bytes()), mime, image.bytes().length);
        } catch (Exception e) {
            log.error("表情上传到对象存储失败: userId={}, error={}", userId, e.getMessage(), e);
            throw new IllegalStateException("表情上传失败，请稍后重试", e);
        }
        UserEmoji emoji = UserEmoji.builder()
                .id(newEmojiId())
                .ownerId(userId)
                .objectKey(objectKey)
                .url(url)
                .name(sanitizeName(name))
                .mime(mime)
                .size((long) image.bytes().length)
                .width(image.width())
                .height(image.height())
                .animated(image.frames() > 1)
                .frameCount(image.frames())
                .contentHash(hash)
                .sortOrder(-System.currentTimeMillis())
                .source(source)
                .sourceMessageId(sourceMessageId)
                .createdAt(new Date())
                .build();
        try {
            repository.insert(emoji);
        } catch (DuplicateKeyException e) {
            throw conflict("这个表情已经在你的表情里了");
        }
        // 并发添加时先插入再复核数量，超出上限的这条回滚
        if (repository.countByOwnerId(userId) > properties.getMaxCount()) {
            repository.delete(emoji);
            throw limitReached();
        }
        return emoji;
    }

    /** 最终要保存的表情文件 */
    record Prepared(byte[] bytes, String format, int width, int height, int frames) {
    }

    /**
     * 按文件内容识别格式并校验：GIF 原样保存（动画保留），必须直接满足大小、尺寸、帧数限制；
     * PNG/JPG 超过尺寸或大小上限时等比缩小（有透明通道存 PNG，否则存 JPG），缩小后仍超大小则拒绝。
     */
    Prepared prepare(byte[] bytes) {
        long maxSize = properties.getMaxSize().toBytes();
        long maxSource = Math.max(maxSize, properties.getMaxSourceSize().toBytes());
        if (bytes.length > maxSource) {
            throw ChatMediaException.tooLarge("表情图片不能超过 " + formatLimit(maxSource));
        }
        int max = properties.getMaxDimension();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw ChatMediaException.unsupportedType("表情只支持 " + formatsLabel() + " 图片");
            }
            ImageReader reader = readers.next();
            try {
                String format = ChatMediaService.normalizeFormat(reader.getFormatName());
                if (!properties.getFormats().contains(format)) {
                    throw ChatMediaException.unsupportedType("表情只支持 " + formatsLabel() + " 图片");
                }
                reader.setInput(input, false, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > properties.getMaxSourcePixels()) {
                    throw ChatMediaException.badRequest("图片尺寸过大");
                }
                if ("gif".equals(format)) {
                    if (bytes.length > maxSize) {
                        throw ChatMediaException.tooLarge("动图表情不能超过 " + formatLimit(maxSize));
                    }
                    if (width > max || height > max) {
                        throw ChatMediaException.badRequest("动图表情尺寸不能超过 " + max + "×" + max + " 像素");
                    }
                    int frames = reader.getNumImages(true);
                    if (frames > properties.getMaxFrames()) {
                        throw ChatMediaException.badRequest("动图帧数不能超过 " + properties.getMaxFrames());
                    }
                    reader.read(0);
                    return new Prepared(bytes, format, width, height, Math.max(1, frames));
                }
                BufferedImage image = reader.read(0);
                if (width <= max && height <= max && bytes.length <= maxSize) {
                    return new Prepared(bytes, format, width, height, 1);
                }
                boolean alpha = image.getColorModel().hasAlpha();
                BufferedImage scaled = ChatMediaService.scaleToFit(image, max, alpha);
                byte[] encoded = alpha ? ChatMediaService.encodePng(scaled) : ChatMediaService.encodeJpeg(scaled);
                if (encoded.length > maxSize) {
                    throw ChatMediaException.tooLarge("表情图片压缩后仍超过 " + formatLimit(maxSize));
                }
                return new Prepared(encoded, alpha ? "png" : "jpeg", scaled.getWidth(), scaled.getHeight(), 1);
            } finally {
                reader.dispose();
            }
        } catch (ChatMediaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ChatMediaException.badRequest("图片已损坏或无法解析");
        }
    }

    private byte[] readObject(String objectKey) {
        long limit = Math.max(properties.getMaxSize().toBytes(), properties.getMaxSourceSize().toBytes());
        try (InputStream in = storage.downloadFile(objectKey)) {
            byte[] bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, limit + 1));
            if (bytes.length > limit) {
                throw ChatMediaException.tooLarge("图片超过表情大小上限 " + formatLimit(limit));
            }
            return bytes;
        } catch (ChatMediaException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取待收藏的对象失败: key={}, error={}", objectKey, e.getMessage());
            throw notFound("图片已不存在");
        }
    }

    String extractEmojiId(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            trimmed = textField(trimmed, "emoji_id");
        }
        return trimmed != null && EMOJI_ID.matcher(trimmed).matches() ? trimmed : null;
    }

    private String textField(String json, String field) {
        if (json == null || !json.trim().startsWith("{")) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json).get(field);
            return node == null || !node.isTextual() ? null : node.asText();
        } catch (JsonProcessingException e) {
            throw ChatMediaException.badRequest("消息内容格式错误");
        }
    }

    /** 只接受服务端生成的表情对象 URL（/onlineim/chats/emojis/...），防止借收藏读取任意对象 */
    static String emojiObjectKeyOf(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = EMOJI_OBJECT_IN_URL.matcher(url);
        return matcher.find() && EMOJI_OBJECT_KEY.matcher(matcher.group(1)).matches() ? matcher.group(1) : null;
    }

    String sanitizeName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").strip();
        if (name.isEmpty()) {
            name = "表情";
        }
        int max = Math.max(1, properties.getNameMaxLength());
        if (name.codePointCount(0, name.length()) > max) {
            name = name.substring(0, name.offsetByCodePoints(0, max));
        }
        return name;
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String formatsLabel() {
        return String.join("、", properties.getFormats().stream()
                .map(f -> "jpeg".equals(f) ? "JPG" : f.toUpperCase(Locale.ROOT)).toList());
    }

    private ChatMediaException limitReached() {
        return conflict("表情数量已达上限（" + properties.getMaxCount() + " 个），请先删除一些再添加");
    }

    private static ChatMediaException conflict(String message) {
        return new ChatMediaException(HttpStatus.CONFLICT, message);
    }

    private static ChatMediaException notFound(String message) {
        return new ChatMediaException(HttpStatus.NOT_FOUND, message);
    }

    private static String formatLimit(long bytes) {
        return bytes % (1024 * 1024) == 0 ? (bytes / 1024 / 1024) + "MB" : (bytes / 1024) + "KB";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String objectBase() {
        byte[] token = new byte[16];
        random.nextBytes(token);
        String month = YearMonth.now(ZoneId.of("Asia/Shanghai")).toString().replace("-", "");
        return "chats/emojis/" + month + "/" + HexFormat.of().formatHex(token);
    }

    private String newEmojiId() {
        byte[] token = new byte[16];
        random.nextBytes(token);
        return "emo_" + HexFormat.of().formatHex(token);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
