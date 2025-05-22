package icu.tianqingyuluo.onlineim.storage;

import icu.tianqingyuluo.onlineim.storage.impl.AliyunOSSAdapter;
import icu.tianqingyuluo.onlineim.storage.impl.MinIOAdapter;
import icu.tianqingyuluo.onlineim.storage.impl.S3Adapter;
import icu.tianqingyuluo.onlineim.storage.impl.TencentCOSAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * OSS适配器工厂类
 * 用于创建不同类型的OSS适配器实例
 */
@Slf4j
public class OSSAdapterFactory {

    /**
     * OSS服务类型枚举
     */
    public enum OSSType {
        MINIO,
        S3,
        ALIYUN,
        TENCENT
    }
    
    /**
     * 创建MinIO适配器
     * 
     * @param endpoint MinIO服务端点URL，如：http://minio.example.com:9000
     * @param accessKey 访问密钥
     * @param secretKey 秘密密钥
     * @return MinIO适配器实例
     */
    public static OSSAdapter createMinIOAdapter(String endpoint, String accessKey, String secretKey) {
        log.info("创建MinIO适配器: {}", endpoint);
        return new MinIOAdapter(endpoint, accessKey, secretKey);
    }
    
    /**
     * 创建Amazon S3适配器
     * 
     * @param endpoint S3服务端点URL，如：https://s3.amazonaws.com
     * @param region 区域，如：us-east-1
     * @param accessKey 访问密钥
     * @param secretKey 秘密密钥
     * @return S3适配器实例
     */
    public static OSSAdapter createS3Adapter(String endpoint, String region, String accessKey, String secretKey) {
        log.info("创建S3适配器: {}, 区域: {}", endpoint, region);
        return new S3Adapter(endpoint, region, accessKey, secretKey);
    }
    
    /**
     * 创建阿里云OSS适配器
     * 
     * @param endpoint OSS服务端点URL，如：https://oss-cn-hangzhou.aliyuncs.com
     * @param accessKeyId 访问密钥ID
     * @param accessKeySecret 访问密钥密钥
     * @param cdnDomain 可选的CDN域名
     * @return 阿里云OSS适配器实例
     */
    public static OSSAdapter createAliyunOSSAdapter(String endpoint, String accessKeyId, String accessKeySecret, String cdnDomain) {
        log.info("创建阿里云OSS适配器: {}", endpoint);
        return new AliyunOSSAdapter(endpoint, accessKeyId, accessKeySecret, cdnDomain);
    }
    
    /**
     * 创建阿里云OSS适配器（不使用CDN）
     * 
     * @param endpoint OSS服务端点URL
     * @param accessKeyId 访问密钥ID
     * @param accessKeySecret 访问密钥密钥
     * @return 阿里云OSS适配器实例
     */
    public static OSSAdapter createAliyunOSSAdapter(String endpoint, String accessKeyId, String accessKeySecret) {
        return createAliyunOSSAdapter(endpoint, accessKeyId, accessKeySecret, null);
    }
    
    /**
     * 创建腾讯云COS适配器
     * 
     * @param region 区域，如：ap-guangzhou
     * @param secretId 密钥ID
     * @param secretKey 密钥Key
     * @param cdnDomain 可选的CDN域名
     * @return 腾讯云COS适配器实例
     */
    public static OSSAdapter createTencentCOSAdapter(String region, String secretId, String secretKey, String cdnDomain) {
        log.info("创建腾讯云COS适配器: 区域 {}", region);
        return new TencentCOSAdapter(region, secretId, secretKey, cdnDomain);
    }
    
    /**
     * 创建腾讯云COS适配器（不使用CDN）
     * 
     * @param region 区域
     * @param secretId 密钥ID
     * @param secretKey 密钥Key
     * @return 腾讯云COS适配器实例
     */
    public static OSSAdapter createTencentCOSAdapter(String region, String secretId, String secretKey) {
        return createTencentCOSAdapter(region, secretId, secretKey, null);
    }
    
    /**
     * 根据OSS类型创建适配器
     * 
     * @param type OSS类型
     * @param endpoint 服务端点URL
     * @param region 区域（S3和腾讯云COS需要）
     * @param accessKey 访问密钥
     * @param secretKey 秘密密钥
     * @param cdnDomain 可选的CDN域名（阿里云OSS和腾讯云COS可用）
     * @return OSS适配器实例
     */
    public static OSSAdapter createAdapter(OSSType type, String endpoint, String region, String accessKey, String secretKey, String cdnDomain) {
        switch (type) {
            case MINIO:
                return createMinIOAdapter(endpoint, accessKey, secretKey);
            case S3:
                return createS3Adapter(endpoint, region, accessKey, secretKey);
            case ALIYUN:
                return createAliyunOSSAdapter(endpoint, accessKey, secretKey, cdnDomain);
            case TENCENT:
                return createTencentCOSAdapter(region, accessKey, secretKey, cdnDomain);
            default:
                throw new IllegalArgumentException("不支持的OSS类型: " + type);
        }
    }
}