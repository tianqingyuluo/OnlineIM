package icu.tianqingyuluo.onlineim.storage.impl;

import icu.tianqingyuluo.onlineim.storage.OSSAdapter;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MinIO对象存储适配器实现
 */
@Slf4j
public class MinIOAdapter implements OSSAdapter {

    private final MinioClient minioClient;
    private final String endpoint;
    private final String providerName;
    private final String publicBaseUrl;

    /**
     * 构造函数
     *
     * @param endpoint MinIO服务端点URL，如：http://minio.example.com:9000
     * @param accessKey 访问密钥
     * @param secretKey 秘密密钥
     */
    public MinIOAdapter(String endpoint, String accessKey, String secretKey) {
        this(endpoint, accessKey, secretKey, null);
    }

    /**
     * @param publicBaseUrl 返回给客户端的文件 URL 前缀（如 http://localhost:8080），为空时返回相对路径 /{bucket}/{object}
     */
    public MinIOAdapter(String endpoint, String accessKey, String secretKey, String publicBaseUrl) {
        this("MinIO", endpoint, accessKey, secretKey, publicBaseUrl);
    }

    protected MinIOAdapter(String providerName, String endpoint, String accessKey, String secretKey, String publicBaseUrl) {
        this.providerName = providerName;
        this.endpoint = endpoint;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Override
    public String uploadFile(String bucketName, String objectName, InputStream inputStream, String contentType, long size) throws Exception {
        try {
            // 确保存储桶存在
            createBucketIfNotExists(bucketName);
            
            // 设置上传对象选项
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .contentType(contentType)
                    .stream(inputStream, size, -1) // -1表示分片大小使用默认值
                    .build();
            
            // 上传文件
            minioClient.putObject(putObjectArgs);
            
            // 返回文件访问URL
            return getFileUrl(bucketName, objectName);
        } catch (Exception e) {
            log.error("{} 上传文件失败: {}", providerName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public InputStream downloadFile(String bucketName, String objectName) throws Exception {
        try {
            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();
            
            return minioClient.getObject(getObjectArgs);
        } catch (Exception e) {
            log.error("{} 下载文件失败: {}", providerName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void deleteFile(String bucketName, String objectName) throws Exception {
        try {
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();
            
            minioClient.removeObject(removeObjectArgs);
        } catch (Exception e) {
            log.error("{} 删除文件失败: {}", providerName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void deleteFiles(String bucketName, List<String> objectNames) throws Exception {
        try {
            List<DeleteObject> objects = new ArrayList<>();
            for (String objectName : objectNames) {
                objects.add(new DeleteObject(objectName));
            }
            
            RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder()
                    .bucket(bucketName)
                    .objects(objects)
                    .build();
            
            // 执行批量删除并处理结果
            Iterable<Result<DeleteError>> results = minioClient.removeObjects(removeObjectsArgs);
            for (Result<DeleteError> result : results) {
                DeleteError error = result.get();
                log.error("{} 批量删除文件失败: 对象 {} 删除失败，错误: {}", providerName, error.objectName(), error.message());
            }
        } catch (Exception e) {
            log.error("{} 批量删除文件失败: {}", providerName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public String getPresignedUrl(String bucketName, String objectName, int expirationTimeSeconds) throws Exception {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(expirationTimeSeconds, TimeUnit.SECONDS)
                    .build();
            
            return minioClient.getPresignedObjectUrl(args);
        } catch (Exception e) {
            log.error("{} 获取预签名 URL 失败: {}", providerName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public boolean doesObjectExist(String bucketName, String objectName) throws Exception {
        try {
            StatObjectArgs statObjectArgs = StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();
            
            minioClient.statObject(statObjectArgs);
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey") || e.errorResponse().code().equals("NoSuchObject")) {
                return false;
            }
            throw e;
        } catch (Exception e) {
            log.error("{} 检查文件是否存在失败: {}", providerName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void createBucketIfNotExists(String bucketName) throws Exception {
        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("创建 {} 存储桶成功: {}", providerName, bucketName);
            }
        } catch (Exception e) {
            log.error("{} 创建存储桶失败: {}", providerName, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 获取文件的完整URL
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 文件URL
     */
    String getFileUrl(String bucketName, String objectName) {
        // 过去这里拼的是后端所在机器/容器的网卡 IP，浏览器（尤其是容器部署时）访问不到；
        // 现在由配置 oss.public-base-url 决定，留空则返回同源相对路径交给前端反向代理。
        return publicBaseUrl + "/" + bucketName + "/" + objectName;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String trimmed = baseUrl.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

}
