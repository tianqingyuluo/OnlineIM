package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.ChatUploadProperties;
import icu.tianqingyuluo.onlineim.exception.ChatMediaException;
import icu.tianqingyuluo.onlineim.pojo.document.ChatUpload;
import icu.tianqingyuluo.onlineim.repository.ChatUploadRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 聊天图片/文件：上传到对象存储（经 OSSAdapterFactory 创建的适配器）、生成图片缩略图、
 * 发送消息时把客户端提交的 file_id 解析成服务端权威的媒体元数据。
 *
 * <p>对象键：{@code chats/images|files/yyyyMM/<128 位随机十六进制>.<ext>}，不包含用户/会话 ID 和原文件名，
 * 不可枚举；浏览器经 {@code /onlineim/**} 代理读取。</p>
 */
@Slf4j
@Service
public class ChatMediaService {

    public static final String KIND_IMAGE = "image";
    public static final String KIND_FILE = "file";
    static final int MAX_NAME_LENGTH = 200;
    private static final float JPEG_QUALITY = 0.82f;

    private final FileStorageService storage;
    private final ChatUploadRepository repository;
    private final ChatUploadProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public ChatMediaService(FileStorageService storage, ChatUploadRepository repository,
                            ChatUploadProperties properties, ObjectMapper objectMapper) {
        this.storage = storage;
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public static boolean isMediaMessage(String messageType) {
        return KIND_IMAGE.equals(messageType) || KIND_FILE.equals(messageType);
    }

    /** 发送媒体消息时由服务端生成的消息内容（JSON 字符串）与扩展字段 */
    public record ResolvedMedia(String content, Map<String, Object> ext) {
    }

    public ChatUpload upload(String userId, String kind, MultipartFile file) {
        if (!KIND_IMAGE.equals(kind) && !KIND_FILE.equals(kind)) {
            throw ChatMediaException.badRequest("type 只能是 image 或 file");
        }
        if (file == null || file.isEmpty()) {
            throw ChatMediaException.badRequest("文件不能为空");
        }
        long limit = (KIND_IMAGE.equals(kind) ? properties.getImageMaxSize() : properties.getFileMaxSize()).toBytes();
        if (file.getSize() > limit) {
            throw ChatMediaException.tooLarge("文件超过大小上限 " + (limit / 1024 / 1024) + "MB");
        }
        String name = sanitizeName(file.getOriginalFilename(), KIND_IMAGE.equals(kind) ? "image" : "file");
        try {
            ChatUpload upload = KIND_IMAGE.equals(kind)
                    ? uploadImage(userId, name, file.getBytes())
                    : uploadFile(userId, name, file);
            return repository.save(upload);
        } catch (ChatMediaException e) {
            throw e;
        } catch (Exception e) {
            log.error("聊天文件上传失败: userId={}, error={}", userId, e.getMessage(), e);
            throw new IllegalStateException("文件上传失败，请稍后重试", e);
        }
    }

    private ChatUpload uploadImage(String userId, String name, byte[] bytes) throws Exception {
        DecodedImage decoded = decode(bytes);
        String base = objectBase("images");
        String objectKey = base + "." + extensionOf(decoded.format());
        String mime = "image/" + decoded.format();
        String url = storage.uploadObject(objectKey, new ByteArrayInputStream(bytes), mime, bytes.length);

        boolean alpha = decoded.image().getColorModel().hasAlpha();
        BufferedImage thumb = scaleToFit(decoded.image(), properties.getThumbnailMaxEdge(), alpha);
        byte[] thumbBytes = alpha ? encodePng(thumb) : encodeJpeg(thumb);
        String thumbKey = base + "_thumb." + (alpha ? "png" : "jpg");
        String thumbUrl = storage.uploadObject(thumbKey, new ByteArrayInputStream(thumbBytes),
                alpha ? "image/png" : "image/jpeg", thumbBytes.length);

        return ChatUpload.builder()
                .id(newUploadId())
                .uploaderId(userId)
                .kind(KIND_IMAGE)
                .objectKey(objectKey)
                .url(url)
                .thumbnailKey(thumbKey)
                .thumbnailUrl(thumbUrl)
                .name(name)
                .size((long) bytes.length)
                .mime(mime)
                .width(decoded.width())
                .height(decoded.height())
                .thumbnailWidth(thumb.getWidth())
                .thumbnailHeight(thumb.getHeight())
                .thumbnailSize((long) thumbBytes.length)
                .createdAt(new Date())
                .build();
    }

    private ChatUpload uploadFile(String userId, String name, MultipartFile file) throws Exception {
        String extension = extensionFromName(name);
        if (extension == null || !properties.getFileExtensions().contains(extension)) {
            throw ChatMediaException.unsupportedType("不支持发送该类型的文件" + (extension == null ? "" : "（." + extension + "）"));
        }
        String objectKey = objectBase("files") + "." + extension;
        String mime = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storage.uploadObject(objectKey, in, mime, file.getSize());
        }
        return ChatUpload.builder()
                .id(newUploadId())
                .uploaderId(userId)
                .kind(KIND_FILE)
                .objectKey(objectKey)
                .url(url)
                .name(name)
                .size(file.getSize())
                .mime(mime)
                .createdAt(new Date())
                .build();
    }

    /**
     * 把客户端提交的媒体消息内容（file_id，或含 file_id 的 JSON）解析成服务端权威元数据。
     * 只能引用自己上传的文件；图片消息只能引用图片上传。
     */
    public ResolvedMedia resolveForMessage(String senderId, String messageType, String content) {
        String fileId = extractFileId(content);
        if (fileId == null) {
            throw ChatMediaException.badRequest("媒体消息缺少 file_id");
        }
        ChatUpload upload = repository.findById(fileId).orElse(null);
        if (upload == null || senderId == null || !senderId.equals(upload.getUploaderId())) {
            throw ChatMediaException.badRequest("文件不存在或不属于当前用户");
        }
        if (KIND_IMAGE.equals(messageType) && !KIND_IMAGE.equals(upload.getKind())) {
            throw ChatMediaException.badRequest("该文件不是图片，不能作为图片消息发送");
        }
        Map<String, Object> media = toMediaMap(upload);
        Map<String, Object> ext = new LinkedHashMap<>(media);
        ext.put("file_name", upload.getName());
        try {
            return new ResolvedMedia(objectMapper.writeValueAsString(media), ext);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("媒体信息序列化失败", e);
        }
    }

    /** 上传接口与消息内容共用的字段（snake_case），值为空的字段省略 */
    public Map<String, Object> toMediaMap(ChatUpload upload) {
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("file_id", upload.getId());
        media.put("type", upload.getKind());
        media.put("url", upload.getUrl());
        putIfPresent(media, "thumbnail_url", upload.getThumbnailUrl());
        media.put("name", upload.getName());
        media.put("size", upload.getSize());
        media.put("mime", upload.getMime());
        putIfPresent(media, "width", upload.getWidth());
        putIfPresent(media, "height", upload.getHeight());
        putIfPresent(media, "thumbnail_width", upload.getThumbnailWidth());
        putIfPresent(media, "thumbnail_height", upload.getThumbnailHeight());
        return media;
    }

    String extractFileId(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed).get("file_id");
                trimmed = node == null || !node.isTextual() ? null : node.asText();
            } catch (JsonProcessingException e) {
                throw ChatMediaException.badRequest("媒体消息内容格式错误");
            }
        }
        return trimmed != null && trimmed.matches("upl_[0-9a-f]{32}") ? trimmed : null;
    }

    private record DecodedImage(BufferedImage image, String format, int width, int height) {
    }

    private DecodedImage decode(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw ChatMediaException.unsupportedType("无法识别的图片格式，可作为文件发送");
            }
            ImageReader reader = readers.next();
            try {
                String format = normalizeFormat(reader.getFormatName());
                if (!properties.getImageFormats().contains(format)) {
                    throw ChatMediaException.unsupportedType("不支持的图片格式: " + format);
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > properties.getImageMaxPixels()) {
                    throw ChatMediaException.badRequest("图片尺寸过大");
                }
                // GIF 等多帧图片只取第一帧生成静态缩略图，原图保持不变
                BufferedImage image = reader.read(0);
                return new DecodedImage(image, format, width, height);
            } finally {
                reader.dispose();
            }
        } catch (ChatMediaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ChatMediaException.badRequest("图片已损坏或无法解析");
        }
    }

    static BufferedImage scaleToFit(BufferedImage source, int maxEdge, boolean alpha) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) maxEdge / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage current = source;
        int currentWidth = width;
        int currentHeight = height;
        // 逐级减半再缩到目标尺寸，避免一次性大比例缩放产生锯齿
        do {
            currentWidth = Math.max(targetWidth, currentWidth / 2);
            currentHeight = Math.max(targetHeight, currentHeight / 2);
            BufferedImage next = new BufferedImage(currentWidth, currentHeight, type);
            Graphics2D g = next.createGraphics();
            if (!alpha) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, currentWidth, currentHeight);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(current, 0, 0, currentWidth, currentHeight, null);
            g.dispose();
            current = next;
        } while (currentWidth != targetWidth || currentHeight != targetHeight);
        return current;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String objectBase(String directory) {
        byte[] token = new byte[16];
        random.nextBytes(token);
        String month = YearMonth.now(ZoneId.of("Asia/Shanghai")).toString().replace("-", "");
        return "chats/" + directory + "/" + month + "/" + HexFormat.of().formatHex(token);
    }

    private String newUploadId() {
        byte[] token = new byte[16];
        random.nextBytes(token);
        return "upl_" + HexFormat.of().formatHex(token);
    }

    static String normalizeFormat(String formatName) {
        String format = formatName == null ? "" : formatName.toLowerCase(Locale.ROOT);
        return "jpg".equals(format) ? "jpeg" : format;
    }

    private static String extensionOf(String format) {
        return "jpeg".equals(format) ? "jpg" : format;
    }

    static String extensionFromName(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 去掉路径、控制字符和首尾空白，限制长度；只用于展示与下载文件名，不参与对象键 */
    static String sanitizeName(String original, String fallback) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").strip();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = fallback;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            String extension = extensionFromName(name);
            String suffix = extension == null || extension.length() > 10 ? "" : "." + extension;
            name = name.substring(0, MAX_NAME_LENGTH - suffix.length()) + suffix;
        }
        return name;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
