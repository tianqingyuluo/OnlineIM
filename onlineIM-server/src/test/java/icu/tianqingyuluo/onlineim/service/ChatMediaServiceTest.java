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
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.upload("usr_a", "voice",
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
