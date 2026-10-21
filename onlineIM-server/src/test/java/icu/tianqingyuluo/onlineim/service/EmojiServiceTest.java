package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.ChatUploadProperties;
import icu.tianqingyuluo.onlineim.config.EmojiProperties;
import icu.tianqingyuluo.onlineim.exception.ChatMediaException;
import icu.tianqingyuluo.onlineim.pojo.document.ChatUpload;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.document.UserEmoji;
import icu.tianqingyuluo.onlineim.repository.ChatUploadRepository;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.repository.UserEmojiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmojiServiceTest {

    private final Map<String, byte[]> stored = new HashMap<>();
    private final Map<String, UserEmoji> emojis = new LinkedHashMap<>();
    private final Map<String, ChatUpload> uploads = new HashMap<>();
    private EmojiProperties properties;
    private PrivateMessageRepository privateMessages;
    private GroupMessageRepository groupMessages;
    private ConversationRepository conversations;
    private GroupMemberService groupMembers;
    private EmojiService service;

    @BeforeEach
    void setUp() throws Exception {
        FileStorageService storage = mock(FileStorageService.class);
        when(storage.uploadObject(anyString(), any(InputStream.class), anyString(), anyLong())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            stored.put(key, ((InputStream) inv.getArgument(1)).readAllBytes());
            return "/onlineim/" + key;
        });
        when(storage.downloadFile(anyString())).thenAnswer(inv -> {
            byte[] bytes = stored.get(inv.<String>getArgument(0));
            if (bytes == null) {
                throw new IllegalStateException("NoSuchKey");
            }
            return new ByteArrayInputStream(bytes);
        });

        UserEmojiRepository repository = mock(UserEmojiRepository.class);
        when(repository.insert(any(UserEmoji.class))).thenAnswer(inv -> {
            UserEmoji emoji = inv.getArgument(0);
            emojis.put(emoji.getId(), emoji);
            return emoji;
        });
        when(repository.countByOwnerId(anyString())).thenAnswer(inv ->
                emojis.values().stream().filter(e -> e.getOwnerId().equals(inv.getArgument(0))).count());
        when(repository.findByIdAndOwnerId(anyString(), anyString())).thenAnswer(inv -> {
            UserEmoji emoji = emojis.get(inv.<String>getArgument(0));
            return emoji != null && emoji.getOwnerId().equals(inv.getArgument(1)) ? emoji : null;
        });
        when(repository.findFirstByOwnerIdAndContentHash(anyString(), anyString())).thenAnswer(inv ->
                emojis.values().stream()
                        .filter(e -> e.getOwnerId().equals(inv.getArgument(0)) && e.getContentHash().equals(inv.getArgument(1)))
                        .findFirst().orElse(null));
        when(repository.findByOwnerIdOrderBySortOrderAscCreatedAtDesc(anyString())).thenAnswer(inv ->
                emojis.values().stream().filter(e -> e.getOwnerId().equals(inv.getArgument(0)))
                        .sorted(Comparator.comparing(UserEmoji::getSortOrder)).toList());
        doAnswer(inv -> emojis.remove(inv.<UserEmoji>getArgument(0).getId())).when(repository).delete(any(UserEmoji.class));
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ChatUploadRepository uploadRepository = mock(ChatUploadRepository.class);
        when(uploadRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(uploads.get(inv.<String>getArgument(0))));
        privateMessages = mock(PrivateMessageRepository.class);
        groupMessages = mock(GroupMessageRepository.class);
        conversations = mock(ConversationRepository.class);
        groupMembers = mock(GroupMemberService.class);

        properties = new EmojiProperties();
        service = new EmojiService(storage, repository, uploadRepository, privateMessages, groupMessages,
                conversations, groupMembers, properties, new ObjectMapper());
    }

    static byte[] image(int width, int height, String format, boolean alpha, int seed) throws Exception {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, ((alpha ? x * 255 / width : 255) << 24) | random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out));
        return out.toByteArray();
    }

    static byte[] animatedGif(int width, int height, int frames) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames; i++) {
                BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        frame.setRGB(x, y, ((x + i * 7) % width) * 255 / width << 16 | (y * 255 / height));
                    }
                }
                writer.writeToSequence(new IIOImage(frame, null, null), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private UserEmoji upload(String user, byte[] bytes, String name) {
        return service.upload(user, new MockMultipartFile("file", name, "application/octet-stream", bytes), null);
    }

    @Test
    void uploadStoresPngUnderEmojiPrefixAndAnimatedGifUnchanged() throws Exception {
        byte[] png = image(120, 80, "png", true, 1);
        UserEmoji emoji = upload("usr_a", png, "../开心.png");
        assertTrue(emoji.getId().matches("emo_[0-9a-f]{32}"));
        assertTrue(emoji.getObjectKey().matches("chats/emojis/\\d{6}/[0-9a-f]{32}\\.png"), emoji.getObjectKey());
        assertEquals("/onlineim/" + emoji.getObjectKey(), emoji.getUrl());
        assertEquals("开心", emoji.getName());
        assertEquals("image/png", emoji.getMime());
        assertEquals(120, emoji.getWidth());
        assertEquals(80, emoji.getHeight());
        assertFalse(emoji.getAnimated());
        assertArrayEquals(png, stored.get(emoji.getObjectKey()));

        byte[] gif = animatedGif(64, 48, 4);
        UserEmoji animated = upload("usr_a", gif, "dance.gif");
        assertTrue(animated.getObjectKey().endsWith(".gif"));
        assertEquals("image/gif", animated.getMime());
        assertTrue(animated.getAnimated());
        assertEquals(4, animated.getFrameCount());
        assertArrayEquals(gif, stored.get(animated.getObjectKey()), "GIF 动图原样保存");

        List<UserEmoji> list = service.list("usr_a");
        assertEquals(List.of(animated.getId(), emoji.getId()), list.stream().map(UserEmoji::getId).toList(), "新表情排在最前");
    }

    @Test
    void largeStaticImageIsScaledDownButLargeGifIsRejected() throws Exception {
        UserEmoji scaled = upload("usr_a", image(2048, 1024, "jpg", false, 2), "photo.jpg");
        assertEquals(1024, scaled.getWidth());
        assertEquals(512, scaled.getHeight());
        assertEquals("image/jpeg", scaled.getMime());
        assertTrue(scaled.getObjectKey().endsWith(".jpg"));

        ChatMediaException tooBig = assertThrows(ChatMediaException.class,
                () -> upload("usr_a", animatedGif(1100, 20, 2), "wide.gif"));
        assertEquals(HttpStatus.BAD_REQUEST, tooBig.getStatus());
        assertTrue(tooBig.getMessage().contains("1024"));

        properties.setMaxSize(DataSize.ofBytes(200));
        ChatMediaException heavy = assertThrows(ChatMediaException.class,
                () -> upload("usr_a", animatedGif(64, 64, 3), "heavy.gif"));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, heavy.getStatus());
    }

    @Test
    void rejectsUnsupportedOrBrokenFiles() throws Exception {
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> upload("usr_a", image(20, 20, "bmp", false, 3), "a.bmp"));
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> upload("usr_a", "<svg/>".getBytes(), "a.png"));
        byte[] png = image(40, 40, "png", false, 4);
        byte[] truncated = java.util.Arrays.copyOf(png, png.length / 2);
        assertStatus(HttpStatus.BAD_REQUEST, () -> upload("usr_a", truncated, "broken.png"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", new MockMultipartFile("file", new byte[0]), null));
        properties.setMaxSourceSize(DataSize.ofBytes(100));
        properties.setMaxSize(DataSize.ofBytes(100));
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE, () -> upload("usr_a", png, "big.png"));
        assertTrue(emojis.isEmpty());
    }

    @Test
    void enforcesPerUserLimitAndDeduplicates() throws Exception {
        properties.setMaxCount(3);
        for (int i = 0; i < 3; i++) {
            upload("usr_a", image(16, 16, "png", false, 10 + i), i + ".png");
        }
        ChatMediaException full = assertThrows(ChatMediaException.class,
                () -> upload("usr_a", image(16, 16, "png", false, 99), "x.png"));
        assertEquals(HttpStatus.CONFLICT, full.getStatus());
        assertTrue(full.getMessage().contains("上限") && full.getMessage().contains("3"), full.getMessage());
        assertEquals(3, service.list("usr_a").size());
        // 别的用户不受影响；同一用户重复图片返回 409
        byte[] same = image(16, 16, "png", false, 10);
        upload("usr_b", same, "same.png");
        ChatMediaException duplicate = assertThrows(ChatMediaException.class, () -> upload("usr_b", same, "again.png"));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatus());
        assertTrue(duplicate.getMessage().contains("已经"));
    }

    @Test
    void deleteAndReorderOnlyTouchOwnEmojis() throws Exception {
        UserEmoji a = upload("usr_a", image(16, 16, "png", false, 21), "a.png");
        Thread.sleep(2);
        UserEmoji b = upload("usr_a", image(16, 16, "png", false, 22), "b.png");
        Thread.sleep(2);
        UserEmoji c = upload("usr_a", image(16, 16, "png", false, 23), "c.png");
        UserEmoji other = upload("usr_b", image(16, 16, "png", false, 24), "o.png");

        List<UserEmoji> ordered = service.reorder("usr_a", List.of(a.getId(), c.getId(), b.getId()));
        assertEquals(List.of(a.getId(), c.getId(), b.getId()), ordered.stream().map(UserEmoji::getId).toList());
        assertEquals(List.of(a.getId(), c.getId(), b.getId()), service.list("usr_a").stream().map(UserEmoji::getId).toList());
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.reorder("usr_a", List.of(a.getId(), b.getId())));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.reorder("usr_a", List.of(a.getId(), b.getId(), other.getId())));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.reorder("usr_a", List.of(a.getId(), a.getId(), b.getId())));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.delete("usr_a", other.getId()));
        service.delete("usr_a", b.getId());
        assertEquals(2, service.list("usr_a").size());
        assertTrue(stored.containsKey(b.getObjectKey()), "删除收藏不删对象，历史消息仍可显示");
        assertStatus(HttpStatus.NOT_FOUND, () -> service.delete("usr_a", b.getId()));
    }

    @Test
    void resolveForMessageRequiresOwnEmojiAndFillsServerFields() throws Exception {
        UserEmoji gif = upload("usr_a", animatedGif(32, 32, 2), "跳舞.gif");
        ChatMediaService.ResolvedMedia resolved = service.resolveForMessage("usr_a",
                "{\"emoji_id\":\"" + gif.getId() + "\",\"url\":\"https://evil.example/x.gif\"}");
        JsonNode content = new ObjectMapper().readTree(resolved.content());
        assertEquals(gif.getId(), content.get("emoji_id").asText());
        assertEquals("emoji", content.get("type").asText());
        assertEquals(gif.getUrl(), content.get("url").asText(), "URL 由服务端填充");
        assertTrue(content.get("animated").asBoolean());
        assertEquals(32, content.get("width").asInt());
        assertEquals(gif.getUrl(), resolved.ext().get("url"));
        assertEquals(gif.getId(), new ObjectMapper().readTree(service.resolveForMessage("usr_a", gif.getId()).content())
                .get("emoji_id").asText());

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_b", gif.getId()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_a", "emo_" + "0".repeat(32)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_a", "hello"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_a", "{bad"));
    }

    @Test
    void saveFromVisibleImageOrEmojiMessageCopiesObject() throws Exception {
        // 私聊图片消息
        byte[] photo = image(1600, 1200, "jpg", false, 31);
        String photoKey = "chats/images/202610/" + "a".repeat(32) + ".jpg";
        stored.put(photoKey, photo);
        uploads.put("upl_" + "1".repeat(32), ChatUpload.builder().id("upl_" + "1".repeat(32)).kind("image")
                .objectKey(photoKey).name("海边.jpg").size((long) photo.length).build());
        PrivateMessage imageMessage = new PrivateMessage();
        imageMessage.setId("msg_1");
        imageMessage.setMessageType("image");
        imageMessage.setStatus(1);
        imageMessage.setContent("{\"file_id\":\"upl_" + "1".repeat(32) + "\"}");
        when(privateMessages.findByIdAndConversationId("msg_1", "pc_ab")).thenReturn(imageMessage);
        when(conversations.findByConversationIdAndUserId("pc_ab", "usr_b")).thenReturn(new Conversation());

        UserEmoji saved = service.saveFromMessage("usr_b", "pc_ab", "msg_1");
        assertEquals("usr_b", saved.getOwnerId());
        assertEquals("海边", saved.getName());
        assertEquals("message", saved.getSource());
        assertEquals("msg_1", saved.getSourceMessageId());
        assertEquals(1024, saved.getWidth(), "大图按上限缩小");
        assertTrue(saved.getObjectKey().startsWith("chats/emojis/"));
        assertStatus(HttpStatus.CONFLICT, () -> service.saveFromMessage("usr_b", "pc_ab", "msg_1"));

        // 非会话成员看不到消息
        assertStatus(HttpStatus.NOT_FOUND, () -> service.saveFromMessage("usr_x", "pc_ab", "msg_1"));

        // 群里的表情消息
        UserEmoji gif = upload("usr_a", animatedGif(40, 40, 3), "鼓掌.gif");
        GroupMessage emojiMessage = new GroupMessage();
        emojiMessage.setId("msg_2");
        emojiMessage.setMessageType("emoji");
        emojiMessage.setStatus(1);
        emojiMessage.setContent(service.resolveForMessage("usr_a", gif.getId()).content());
        when(groupMessages.findByIdAndGroupId("msg_2", "grp_1")).thenReturn(emojiMessage);
        when(groupMembers.isGroupMember("grp_1", "usr_c")).thenReturn(true);
        UserEmoji copy = service.saveFromMessage("usr_c", "grp_1", "msg_2");
        assertNotEquals(gif.getObjectKey(), copy.getObjectKey(), "收藏会复制对象，原作者删除不影响");
        assertTrue(copy.getAnimated());
        assertEquals("鼓掌", copy.getName());
        assertArrayEquals(stored.get(gif.getObjectKey()), stored.get(copy.getObjectKey()));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.saveFromMessage("usr_d", "grp_1", "msg_2"));

        // 撤回的、文本消息、伪造 URL 的表情不能收藏
        emojiMessage.setStatus(3);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.saveFromMessage("usr_c", "grp_1", "msg_2"));
        GroupMessage text = new GroupMessage();
        text.setMessageType("text");
        text.setContent("hi");
        when(groupMessages.findByIdAndGroupId("msg_3", "grp_1")).thenReturn(text);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.saveFromMessage("usr_c", "grp_1", "msg_3"));
        GroupMessage forged = new GroupMessage();
        forged.setMessageType("emoji");
        forged.setContent("{\"url\":\"/onlineim/avatars/secret.png\"}");
        when(groupMessages.findByIdAndGroupId("msg_4", "grp_1")).thenReturn(forged);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.saveFromMessage("usr_c", "grp_1", "msg_4"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.saveFromMessage("usr_c", "grp_1", null));
    }

    @Test
    void chatMediaServiceDelegatesEmojiMessages() throws Exception {
        UserEmoji emoji = upload("usr_a", image(16, 16, "png", false, 41), "a.png");
        ChatMediaService media = new ChatMediaService(mock(FileStorageService.class), mock(ChatUploadRepository.class),
                new ChatUploadProperties(), new ObjectMapper());
        assertTrue(ChatMediaService.isMediaMessage("emoji"));
        assertTrue(ChatMediaService.isMediaMessage("sticker"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> media.resolveForMessage("usr_a", "emoji", emoji.getId()));
        media.setEmojiService(service);
        assertTrue(media.resolveForMessage("usr_a", "sticker", emoji.getId()).content().contains(emoji.getUrl()));
    }

    @Test
    void helpers() {
        assertEquals("chats/emojis/202610/" + "b".repeat(32) + ".gif",
                EmojiService.emojiObjectKeyOf("/onlineim/chats/emojis/202610/" + "b".repeat(32) + ".gif"));
        assertNull(EmojiService.emojiObjectKeyOf("/onlineim/chats/images/202610/" + "b".repeat(32) + ".gif"));
        assertNull(EmojiService.emojiObjectKeyOf("/onlineim/chats/emojis/../avatars/x.png"));
        assertNull(EmojiService.emojiObjectKeyOf(null));
        assertEquals("[表情]", ConversationRecordService.preview("emoji", "{}"));
        assertEquals("[表情]", ConversationRecordService.preview("sticker", "{}"));
        assertEquals("表情", service.sanitizeName("  "));
        assertEquals(32, service.sanitizeName("长".repeat(50)).length());
        assertEquals(new ArrayList<>(List.of("png", "jpeg", "gif")), properties.getFormats());
    }

    private static void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable executable) {
        ChatMediaException e = assertThrows(ChatMediaException.class, executable);
        assertEquals(expected, e.getStatus(), e.getMessage());
    }
}
