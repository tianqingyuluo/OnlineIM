package icu.tianqingyuluo.onlineim.config;

import icu.tianqingyuluo.onlineim.storage.OSSAdapter;
import icu.tianqingyuluo.onlineim.storage.OSSAdapterFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OSS配置类
 * 用于从配置文件中读取OSS相关的配置信息，并提供创建OSS适配器的方法
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "oss")
public class OSSConfig {

    /**
     * OSS类型：rustfs, minio, s3, aliyun, tencent
     */
    private String type;

    /**
     * 服务端点URL
     */
    private String endpoint;

    /**
     * 区域（S3和腾讯云COS需要）
     */
    private String region;

    /**
     * 访问密钥
     */
    private String accessKey;

    /**
     * 秘密密钥
     */
    private String secretKey;

    /**
     * CDN域名（可选）
     */
    private String cdnDomain;

    /**
     * 默认存储桶名称
     */
    private String defaultBucket;

    /**
     * 返回给客户端的文件 URL 前缀（RustFS / MinIO 适用），文件经后端 FileProxyController 的
     * /{bucket}/... 路由读出。例如 http://localhost:8080；留空则返回同源相对路径 /{bucket}/{object}，
     * 由前端所在的反向代理（如 nginx 的 /onlineim/）转发到后端。
     */
    private String publicBaseUrl;

    /**
     * 创建OSS适配器Bean
     *
     * @return OSS适配器实例
     */
    @Bean
    public OSSAdapter ossAdapter() {
        log.info("初始化OSS适配器: 类型={}, 端点={}", type, endpoint);

        OSSAdapterFactory.OSSType ossType;
        try {
            ossType = OSSAdapterFactory.OSSType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("不支持的 OSS 类型: {}, 将使用 RustFS 作为默认类型", type);
            ossType = OSSAdapterFactory.OSSType.RUSTFS;
        }

        return OSSAdapterFactory.createAdapter(ossType, endpoint, region, accessKey, secretKey, cdnDomain, publicBaseUrl);
    }
}
