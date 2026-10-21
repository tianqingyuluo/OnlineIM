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
            MediaType contentType = MediaTypeFactory.getMediaType(objectName)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .body(bytes);
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
