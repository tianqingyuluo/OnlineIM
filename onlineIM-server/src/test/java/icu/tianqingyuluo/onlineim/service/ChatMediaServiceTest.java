package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.ChatUploadProperties;
import icu.tianqingyuluo.onlineim.exception.ChatMediaException;
import icu.tianqingyuluo.onlineim.pojo.document.ChatUpload;
import icu.tianqingyuluo.onlineim.repository.ChatUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatMediaServiceTest {

    private FileStorageService storage;
    private ChatUploadRepository repository;
    private ChatUploadProperties properties;
    private ChatMediaService service;
    private final Map<String, byte[]> stored = new HashMap<>();
    private final Map<String, String> storedMime = new HashMap<>();
    private final Map<String, ChatUpload> uploads = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        storage = mock(FileStorageService.class);
        repository = mock(ChatUploadRepository.class);
        properties = new ChatUploadProperties();
        when(storage.uploadObject(anyString(), any(InputStream.class), anyString(), anyLong())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            stored.put(key, ((InputStream) inv.getArgument(1)).readAllBytes());
            storedMime.put(key, inv.getArgument(2));
            return "/onlineim/" + key;
        });
        when(repository.save(any(ChatUpload.class))).thenAnswer(inv -> {
            ChatUpload upload = inv.getArgument(0);
            uploads.put(upload.getId(), upload);
            return upload;
        });
        when(repository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(uploads.get(inv.<String>getArgument(0))));
        service = new ChatMediaService(storage, repository, properties, new ObjectMapper());
    }

    private static byte[] noisyImage(int width, int height, String format, boolean alpha) throws Exception {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = alpha ? (x * 255 / width) : 255;
                image.setRGB(x, y, (a << 24) | (random.nextInt(0xFFFFFF)));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out));
        return out.toByteArray();
    }

    @Test
    void imageUploadStoresOriginalAndSmallerThumbnailUnderChatsPrefix() throws Exception {
        byte[] png = noisyImage(1200, 800, "png", false);
        ChatUpload upload = service.upload("usr_a", "image", new MockMultipartFile("file", "../../夏天.png", "text/html", png));

        assertTrue(upload.getId().matches("upl_[0-9a-f]{32}"));
        assertTrue(upload.getObjectKey().matches("chats/images/\\d{6}/[0-9a-f]{32}\\.png"), upload.getObjectKey());
        assertEquals(upload.getObjectKey().replace(".png", "_thumb.jpg"), upload.getThumbnailKey());
        assertEquals("/onlineim/" + upload.getObjectKey(), upload.getUrl());
        assertEquals("夏天.png", upload.getName());
        assertEquals("image/png", upload.getMime(), "MIME 由内容识别，不信任客户端");
        assertEquals(1200, upload.getWidth());
        assertEquals(800, upload.getHeight());
        assertEquals(320, upload.getThumbnailWidth());
        assertEquals(213, upload.getThumbnailHeight());
        assertArrayEquals(png, stored.get(upload.getObjectKey()));
        byte[] thumb = stored.get(upload.getThumbnailKey());
        assertTrue(thumb.length < png.length);
        BufferedImage decodedThumb = ImageIO.read(new ByteArrayInputStream(thumb));
        assertEquals(320, decodedThumb.getWidth());
        assertEquals("image/jpeg", storedMime.get(upload.getThumbnailKey()));

        ChatUpload second = service.upload("usr_a", "image", new MockMultipartFile("file", "夏天.png", "image/png", png));
        assertNotEquals(upload.getObjectKey(), second.getObjectKey(), "对象 key 随机且不可猜测");
    }

    @Test
    void transparentImageGetsPngThumbnailAndSmallImageIsNotUpscaled() throws Exception {
        ChatUpload upload = service.upload("usr_a", "image",
                new MockMultipartFile("file", "a.png", "image/png", noisyImage(100, 50, "png", true)));
        assertTrue(upload.getThumbnailKey().endsWith("_thumb.png"));
        assertEquals(100, upload.getThumbnailWidth());
        assertEquals(50, upload.getThumbnailHeight());
    }

    @Test
    void gifAndJpegAreAccepted() throws Exception {
        ChatUpload gif = service.upload("usr_a", "image",
                new MockMultipartFile("file", "a.gif", "image/gif", noisyImage(640, 640, "gif", false)));
        assertEquals("image/gif", gif.getMime());
        assertTrue(gif.getObjectKey().endsWith(".gif"));
        ChatUpload jpeg = service.upload("usr_a", "image",
                new MockMultipartFile("file", "photo.JPG", "image/jpeg", noisyImage(400, 1000, "jpg", false)));
        assertEquals("image/jpeg", jpeg.getMime());
        assertTrue(jpeg.getObjectKey().endsWith(".jpg"));
        assertEquals(128, jpeg.getThumbnailWidth());
        assertEquals(320, jpeg.getThumbnailHeight());
    }

    @Test
    void rejectsInvalidUploadsWithMatchingStatus() {
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> service.upload("usr_a", "image",
                new MockMultipartFile("file", "x.png", "image/png", "<html>not an image</html>".getBytes())));
        byte[] corrupt = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R', 1, 2};
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "image",
                new MockMultipartFile("file", "x.png", "image/png", corrupt)));
        properties.setFileMaxSize(DataSize.ofBytes(10));
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE, () -> service.upload("usr_a", "file",
                new MockMultipartFile("file", "a.txt", "text/plain", new byte[11])));
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> service.upload("usr_a", "file",
                new MockMultipartFile("file", "evil.html", "text/html", "<script>".getBytes())));
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> service.upload("usr_a", "file",
                new MockMultipartFile("file", "noext", "text/plain", "x".getBytes())));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "file",
                new MockMultipartFile("file", "a.txt", "text/plain", new byte[0])));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "audio",
                new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes())));
        verify(repository, never()).save(any());
    }

    @Test
    void fileUploadKeepsBytesAndDerivesMimeFromExtension() {
        byte[] bytes = "hello,世界\n".getBytes();
        ChatUpload upload = service.upload("usr_a", "file", new MockMultipartFile("file", "C:\\tmp\\报告.CSV", "application/x-evil", bytes));
        assertTrue(upload.getObjectKey().matches("chats/files/\\d{6}/[0-9a-f]{32}\\.csv"), upload.getObjectKey());
        assertEquals("报告.CSV", upload.getName());
        assertEquals("text/csv", upload.getMime());
        assertEquals(bytes.length, upload.getSize());
        assertNull(upload.getThumbnailUrl());
        assertArrayEquals(bytes, stored.get(upload.getObjectKey()));
    }

    @Test
    void resolveUsesServerMetadataAndChecksOwnershipAndKind() throws Exception {
        ChatUpload image = service.upload("usr_a", "image",
                new MockMultipartFile("file", "a.png", "image/png", noisyImage(500, 500, "png", false)));
        ChatUpload file = service.upload("usr_a", "file",
                new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()));

        ChatMediaService.ResolvedMedia resolved = service.resolveForMessage("usr_a", "image",
                "{\"file_id\":\"" + image.getId() + "\",\"url\":\"https://evil.example/x.png\"}");
        Map<?, ?> content = new ObjectMapper().readValue(resolved.content(), Map.class);
        assertEquals(image.getUrl(), content.get("url"));
        assertEquals(image.getThumbnailUrl(), content.get("thumbnail_url"));
        assertEquals(500, content.get("width"));
        assertEquals("a.png", resolved.ext().get("file_name"));

        assertEquals("a.pdf", service.resolveForMessage("usr_a", "file", file.getId()).ext().get("file_name"));
        assertNotNull(service.resolveForMessage("usr_a", "file", image.getId()), "图片也可以作为文件发送");

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_b", "image", image.getId()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_a", "image", file.getId()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_a", "file", "upl_ffffffffffffffffffffffffffffffff"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_a", "file", "https://example.com/a.pdf"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_a", "file", "{broken"));
    }

    @Test
    void refreshForForwardReusesUploadWithoutOwnershipCheckAndFallsBack() throws Exception {
        ChatUpload image = service.upload("usr_a", "image",
                new MockMultipartFile("file", "a.png", "image/png", noisyImage(300, 200, "png", false)));

        // 转发者 usr_b 不是上传者，也能按引用复用；元数据来自上传记录而非来源内容
        ChatMediaService.ResolvedMedia forwarded = service.refreshForForward("image",
                "{\"file_id\":\"" + image.getId() + "\",\"url\":\"https://evil.example/x.png\"}", Map.of());
        Map<?, ?> content = new ObjectMapper().readValue(forwarded.content(), Map.class);
        assertEquals(image.getUrl(), content.get("url"));
        assertEquals(300, content.get("width"));
        assertEquals("a.png", forwarded.ext().get("file_name"));

        // 找不到上传记录 / 非媒体消息：原样沿用
        Map<String, Object> ext = Map.of("file_name", "old.pdf");
        ChatMediaService.ResolvedMedia legacy = service.refreshForForward("file", "{\"url\":\"/onlineim/old.pdf\"}", ext);
        assertEquals("{\"url\":\"/onlineim/old.pdf\"}", legacy.content());
        assertEquals("old.pdf", legacy.ext().get("file_name"));
        assertEquals("hi", service.refreshForForward("text", "hi", null).content());
        assertEquals("{\"emoji_id\":\"emj_1\"}", service.refreshForForward("emoji", "{\"emoji_id\":\"emj_1\"}", null).content());
    }

    // ---------------- 语音 ----------------

    private static byte[] audio(String name) throws Exception {
        return icu.tianqingyuluo.onlineim.util.AudioProbeTest.fixture(name);
    }

    @Test
    void voiceUploadDetectsFormatStoresUnderVoicesAndUsesParsedDuration() throws Exception {
        byte[] webm = audio("chrome-mediarecorder.webm");
        // 客户端谎报 30 秒、Content-Type 和扩展名都不对：以内容和解析时长为准
        ChatUpload upload = service.upload("usr_a", "voice",
                new MockMultipartFile("file", "录音.mp3", "text/html", webm), 30_000L);
        assertTrue(upload.getObjectKey().matches("chats/voices/\\d{6}/[0-9a-f]{32}\\.webm"), upload.getObjectKey());
        assertEquals("audio/webm", upload.getMime());
        assertEquals("voice", upload.getKind());
        assertEquals("parsed", upload.getDurationSource());
        assertEquals("opus", upload.getCodec());
        assertTrue(Math.abs(upload.getDurationMs() - 2000) < 700, String.valueOf(upload.getDurationMs()));
        assertArrayEquals(webm, stored.get(upload.getObjectKey()));
        assertEquals("audio/webm", storedMime.get(upload.getObjectKey()));

        Map<String, Object> media = service.toMediaMap(upload);
        assertEquals(upload.getDurationMs(), media.get("duration_ms"));
        assertEquals(Math.max(1L, Math.round(upload.getDurationMs() / 1000.0)), media.get("duration"));

        for (String name : new String[]{"tone-1500ms-opus.ogg", "tone-1500ms.m4a", "tone-1500ms.mp3"}) {
            ChatUpload other = service.upload("usr_a", "voice", new MockMultipartFile("file", "v", "application/octet-stream", audio(name)), null);
            assertTrue(Math.abs(other.getDurationMs() - 1500) < 150, name + " " + other.getDurationMs());
        }
        ChatUpload wav = service.upload("usr_a", "voice",
                new MockMultipartFile("file", "a.wav", "audio/wav", icu.tianqingyuluo.onlineim.util.AudioProbeTest.wav(8000, 3000)), null);
        assertEquals(3000, wav.getDurationMs());
        assertTrue(wav.getObjectKey().endsWith(".wav"));
    }

    @Test
    void voiceWithoutParsableDurationUsesValidatedClientDuration() throws Exception {
        byte[] fragmented = audio("tone-1500ms-fragmented.m4a");
        ChatUpload upload = service.upload("usr_a", "voice", new MockMultipartFile("file", "v.m4a", "audio/mp4", fragmented), 1500L);
        assertEquals(1500, upload.getDurationMs());
        assertEquals("client", upload.getDurationSource());
        assertTrue(upload.getObjectKey().endsWith(".m4a"));
        // 缺少时长 / 时长与大小明显不符 / 超长 / 太短
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "voice", new MockMultipartFile("file", "v.m4a", "audio/mp4", fragmented), null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "voice", new MockMultipartFile("file", "v.m4a", "audio/mp4", fragmented), 59_000L));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "voice", new MockMultipartFile("file", "v.m4a", "audio/mp4", fragmented), 1L));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "voice", new MockMultipartFile("file", "v.m4a", "audio/mp4", fragmented), 120_000L));
    }

    @Test
    void rejectsInvalidVoiceUploads() throws Exception {
        // 超过 60 秒（解析值）
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "voice",
                new MockMultipartFile("file", "long.wav", "audio/wav", icu.tianqingyuluo.onlineim.util.AudioProbeTest.wav(8000, 62_000)), 10_000L));
        // 太短
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "voice",
                new MockMultipartFile("file", "short.wav", "audio/wav", icu.tianqingyuluo.onlineim.util.AudioProbeTest.wav(8000, 200)), null));
        // 伪造：HTML/PNG/视频
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> service.upload("usr_a", "voice",
                new MockMultipartFile("file", "x.webm", "audio/webm", "<html>not audio</html>".getBytes()), 1000L));
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> service.upload("usr_a", "voice",
                new MockMultipartFile("file", "x.webm", "audio/webm", noisyImage(10, 10, "png", false)), 1000L));
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> service.upload("usr_a", "voice",
                new MockMultipartFile("file", "x.webm", "video/webm", audio("video-1s.webm")), 1000L));
        // 损坏
        byte[] truncated = java.util.Arrays.copyOf(audio("tone-1500ms-opus.ogg"), 100);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "voice",
                new MockMultipartFile("file", "x.ogg", "audio/ogg", truncated), 1000L));
        // 不在允许列表
        properties.setVoiceFormats(new java.util.ArrayList<>(java.util.List.of("webm", "ogg")));
        assertStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> service.upload("usr_a", "voice",
                new MockMultipartFile("file", "x.mp3", "audio/mpeg", audio("tone-1500ms.mp3")), null));
        // 超过大小
        properties.setVoiceMaxSize(DataSize.ofKilobytes(2));
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE, () -> service.upload("usr_a", "voice",
                new MockMultipartFile("file", "x.webm", "audio/webm", audio("tone-1500ms.webm")), null));
        verify(repository, never()).save(any());
        assertTrue(stored.isEmpty(), "被拒绝的语音不会写入对象存储");
    }

    @Test
    void voiceMessageMustReferenceOwnVoiceUpload() throws Exception {
        ChatUpload voice = service.upload("usr_a", "voice",
                new MockMultipartFile("file", "v.webm", "audio/webm", audio("tone-1500ms.webm")), null);
        ChatUpload image = service.upload("usr_a", "image",
                new MockMultipartFile("file", "a.png", "image/png", noisyImage(50, 50, "png", false)));
        ChatMediaService.ResolvedMedia resolved = service.resolveForMessage("usr_a", "voice",
                "{\"file_id\":\"" + voice.getId() + "\",\"duration_ms\":999999,\"url\":\"https://evil.example/a.webm\"}");
        Map<?, ?> content = new ObjectMapper().readValue(resolved.content(), Map.class);
        assertEquals(voice.getUrl(), content.get("url"));
        assertEquals("voice", content.get("type"));
        assertEquals(voice.getDurationMs().intValue(), ((Number) content.get("duration_ms")).intValue(), "时长取服务端记录，忽略客户端");
        assertEquals(voice.getDurationMs().intValue(), ((Number) resolved.ext().get("duration_ms")).intValue());
        assertTrue(ChatMediaService.isMediaMessage("voice"));

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_b", "voice", voice.getId()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_a", "voice", image.getId()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.resolveForMessage("usr_a", "image", voice.getId()));
        assertNotNull(service.resolveForMessage("usr_a", "file", voice.getId()), "语音也可以作为文件发送");
    }

    @Test
    void helpers() {
        assertEquals("a.txt", ChatMediaService.sanitizeName("/etc/../a.txt", "file"));
        assertEquals("ab.txt", ChatMediaService.sanitizeName("a\u0000b.txt", "file"));
        assertEquals("file", ChatMediaService.sanitizeName("  ", "file"));
        assertEquals(ChatMediaService.MAX_NAME_LENGTH, ChatMediaService.sanitizeName("x".repeat(500) + ".txt", "file").length());
        assertTrue(ChatMediaService.sanitizeName("x".repeat(500) + ".txt", "file").endsWith(".txt"));
        assertEquals("jpeg", ChatMediaService.normalizeFormat("JPG"));
        assertEquals("pdf", ChatMediaService.extensionFromName("a.b.PDF"));
        assertNull(ChatMediaService.extensionFromName("README"));
        BufferedImage source = new BufferedImage(3000, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 3000, 1000);
        g.dispose();
        BufferedImage scaled = ChatMediaService.scaleToFit(source, 320, false);
        assertEquals(320, scaled.getWidth());
        assertEquals(107, scaled.getHeight());
    }

    private static void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable executable) {
        ChatMediaException e = assertThrows(ChatMediaException.class, executable);
        assertEquals(expected, e.getStatus());
    }
}
