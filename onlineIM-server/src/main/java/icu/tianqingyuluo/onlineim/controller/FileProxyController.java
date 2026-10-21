package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.util.AntPathMatcher;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * 对象存储文件代理：浏览器通过 /onlineim/{object} 读取默认存储桶里的头像、群头像和聊天文件，
 * 不需要直接访问对象存储（RustFS/MinIO 通常只在内网可达）。
 */
@Slf4j
@RestController
public class FileProxyController {

    /** 允许经代理公开读取的对象前缀（与 FileStorageService 的目录约定一致） */
    static final List<String> PUBLIC_PREFIXES = List.of("avatars/", "groups/avatars/", "chats/");

    private final FileStorageService fileStorageService;
    private final String bucket;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public FileProxyController(FileStorageService fileStorageService,
                               @Value("${oss.default-bucket:onlineim}") String bucket) {
        this.fileStorageService = fileStorageService;
        this.bucket = bucket;
    }

    @GetMapping("/onlineim/**")
    public ResponseEntity<byte[]> file(HttpServletRequest request) {
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String objectName = pathMatcher.extractPathWithinPattern(pattern, path);
        if (!isPublicObject(objectName)) {
            return ResponseEntity.notFound().build();
        }
        try (InputStream inputStream = fileStorageService.downloadFile(bucket, objectName)) {
            if (inputStream == null) {
                log.warn("File not found: {}", objectName);
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = inputStream.readAllBytes();
            MediaType contentType = contentTypeOf(objectName);
            long[] range = parseRange(request.getHeader(HttpHeaders.RANGE), bytes.length);
            if (range != null && range.length == 0) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + bytes.length)
                        .build();
            }
            ResponseEntity.BodyBuilder response = (range == null ? ResponseEntity.ok() : ResponseEntity.status(HttpStatus.PARTIAL_CONTENT))
                    .contentType(contentType)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header("X-Content-Type-Options", "nosniff");
            if (isDownloadOnly(objectName)) {
                // 聊天文件一律作为附件下载，避免同源下 HTML/SVG 等被浏览器直接渲染执行
                response.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().build().toString());
            }
            if (range != null) {
                // 单段 Range（音频播放器拖动进度时使用）
                response.header(HttpHeaders.CONTENT_RANGE, "bytes " + range[0] + "-" + range[1] + "/" + bytes.length);
                return response.body(java.util.Arrays.copyOfRange(bytes, (int) range[0], (int) range[1] + 1));
            }
            return response.body(bytes);
        } catch (Exception e) {
            if (isNotFound(e)) {
                log.warn("File not found: {}", objectName);
                return ResponseEntity.notFound().build();
            }
            log.error("Error retrieving file {}: {}", objectName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    static boolean isPublicObject(String objectName) {
        if (objectName == null || objectName.isBlank() || objectName.contains("..") || objectName.contains("\\")) {
            return false;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(objectName::startsWith);
    }

    /** 语音对象按扩展名给出音频类型（.webm 默认会被识别成 video/webm） */
    static MediaType contentTypeOf(String objectName) {
        if (objectName.startsWith("chats/voices/")) {
            String lower = objectName.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".webm")) return MediaType.parseMediaType("audio/webm");
            if (lower.endsWith(".ogg")) return MediaType.parseMediaType("audio/ogg");
            if (lower.endsWith(".m4a")) return MediaType.parseMediaType("audio/mp4");
            if (lower.endsWith(".mp3")) return MediaType.parseMediaType("audio/mpeg");
            if (lower.endsWith(".wav")) return MediaType.parseMediaType("audio/wav");
        }
        return MediaTypeFactory.getMediaType(objectName).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * 解析单段 {@code Range: bytes=a-b}。无/不支持的 Range 返回 null（按 200 返回全文），
     * 越界返回空数组（416），否则返回 {start, end}（含 end）。
     */
    static long[] parseRange(String header, long length) {
        if (header == null || !header.startsWith("bytes=") || header.contains(",")) {
            return null;
        }
        String spec = header.substring(6).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        try {
            String first = spec.substring(0, dash).trim();
            String last = spec.substring(dash + 1).trim();
            long start;
            long end;
            if (first.isEmpty()) {
                if (last.isEmpty()) return null;
                long suffix = Long.parseLong(last);
                if (suffix <= 0) return new long[0];
                start = Math.max(0, length - suffix);
                end = length - 1;
            } else {
                start = Long.parseLong(first);
                end = last.isEmpty() ? length - 1 : Math.min(Long.parseLong(last), length - 1);
            }
            if (start >= length || start > end || start < 0) {
                return new long[0];
            }
            return new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean isDownloadOnly(String objectName) {
        return objectName.startsWith("chats/files/");
    }

    private static boolean isNotFound(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && (message.contains("NoSuchKey") || message.contains("does not exist")
                    || message.contains("NoSuchBucket"))) {
                return true;
            }
        }
        return false;
    }
}
