package icu.tianqingyuluo.onlineim.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;

/**
 * 聊天文件/图片上传限制，配置前缀 {@code im.upload}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "im.upload")
public class ChatUploadProperties {

    /** 图片大小上限 */
    private DataSize imageMaxSize = DataSize.ofMegabytes(10);

    /** 普通文件大小上限 */
    private DataSize fileMaxSize = DataSize.ofMegabytes(20);

    /** 允许的图片格式（按文件内容识别，不信任扩展名和客户端 Content-Type） */
    private List<String> imageFormats = new ArrayList<>(List.of("jpeg", "png", "gif", "bmp"));

    /** 允许作为文件发送的扩展名（小写，不含点） */
    private List<String> fileExtensions = new ArrayList<>(List.of(
            "pdf", "txt", "md", "csv", "json", "xml", "log",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf",
            "zip", "rar", "7z", "tar", "gz",
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "mp3", "wav", "ogg", "m4a", "mp4", "mov", "webm"));

    /** 缩略图最长边（像素），按比例缩放，不放大 */
    private int thumbnailMaxEdge = 320;

    /** 语音大小上限 */
    private DataSize voiceMaxSize = DataSize.ofMegabytes(5);

    /** 允许的语音容器格式（按文件内容识别）：webm、ogg、mp4、mp3、wav */
    private List<String> voiceFormats = new ArrayList<>(List.of("webm", "ogg", "mp4", "mp3", "wav"));

    /** 语音最长时长 */
    private Duration voiceMaxDuration = Duration.ofSeconds(60);

    /** 语音最短时长 */
    private Duration voiceMinDuration = Duration.ofMillis(500);

    /** 图片最大像素数（宽 × 高），防止解压炸弹 */
    private long imageMaxPixels = 40_000_000L;
}
