package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

@Slf4j
@RestController
public class FileProxyController {

    private final FileStorageService fileStorageService;

    public FileProxyController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping(value = "/onlineim/avatars/{userId}/{filename}")
    public ResponseEntity<byte[]> avatar(@PathVariable String userId, @PathVariable String filename) {
        String objectName = "avatars/" + userId + "/" + filename;
        try (InputStream inputStream = fileStorageService.downloadFile("onlineim", objectName)) {
            if (inputStream == null) {
                log.warn("Avatar not found: {}", objectName);
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = inputStream.readAllBytes();

            HttpHeaders headers = new HttpHeaders();
            String extension = "";
            int i = filename.lastIndexOf('.');
            if (i > 0 && i < filename.length() - 1) {
                extension = filename.substring(i + 1).toLowerCase();
            }

            switch (extension) {
                case "jpeg":
                case "jpg":
                    headers.setContentType(MediaType.IMAGE_JPEG);
                    break;
                case "png":
                    headers.setContentType(MediaType.IMAGE_PNG);
                    break;
                case "gif":
                    headers.setContentType(MediaType.IMAGE_GIF);
                    break;
                default:
                    // Fallback for unknown types, or could return an error
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    break;
            }
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error retrieving avatar {}: {}", objectName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
