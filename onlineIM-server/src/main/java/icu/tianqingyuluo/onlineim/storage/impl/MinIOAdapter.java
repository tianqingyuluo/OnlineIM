package icu.tianqingyuluo.onlineim.storage.impl;

import icu.tianqingyuluo.onlineim.storage.OSSAdapter;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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

    /**
     * 构造函数
     *
     * @param endpoint MinIO服务端点URL，如：http://minio.example.com:9000
     * @param accessKey 访问密钥
     * @param secretKey 秘密密钥
     */
    public MinIOAdapter(String endpoint, String accessKey, String secretKey) {
        this.endpoint = endpoint;
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
            log.error("MinIO上传文件失败: {}", e.getMessage(), e);
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
            log.error("MinIO下载文件失败: {}", e.getMessage(), e);
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
            log.error("MinIO删除文件失败: {}", e.getMessage(), e);
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
                log.error("MinIO批量删除文件失败: 对象 {} 删除失败，错误: {}", error.objectName(), error.message());
            }
        } catch (Exception e) {
            log.error("MinIO批量删除文件失败: {}", e.getMessage(), e);
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
            log.error("MinIO获取预签名URL失败: {}", e.getMessage(), e);
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
            log.error("MinIO检查文件是否存在失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void createBucketIfNotExists(String bucketName) throws Exception {
        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("创建MinIO存储桶成功: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("MinIO创建存储桶失败: {}", e.getMessage(), e);
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
    private String getFileUrl(String bucketName, String objectName) {
        return String.format("%s/%s/%s", endpoint, bucketName, objectName);
    }
}