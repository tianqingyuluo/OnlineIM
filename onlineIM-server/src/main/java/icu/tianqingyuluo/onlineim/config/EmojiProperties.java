package icu.tianqingyuluo.onlineim.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义表情限制，配置前缀 {@code im.emoji}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "im.emoji")
public class EmojiProperties {

    /** 每个用户最多收藏的表情数量 */
    private int maxCount = 100;

    /** 保存后的单个表情文件大小上限（GIF 动图原样保存，必须直接满足） */
    private DataSize maxSize = DataSize.ofMegabytes(2);

    /** 静态图（PNG/JPG）源文件大小上限；超过 max-size 或 max-dimension 的静态图会被等比缩小后保存 */
    private DataSize maxSourceSize = DataSize.ofMegabytes(10);

    /** 源图像素总数上限，防止解压炸弹 */
    private long maxSourcePixels = 40_000_000L;

    /** 宽、高各自的上限（像素）；GIF 超出直接拒绝，静态图自动缩小 */
    private int maxDimension = 1024;

    /** 动图帧数上限 */
    private int maxFrames = 300;

    /** 允许的图片格式（按文件内容识别，jpg 统一记为 jpeg） */
    private List<String> formats = new ArrayList<>(List.of("png", "jpeg", "gif"));

    /** 表情名称最大长度（字符） */
    private int nameMaxLength = 32;
}
