package icu.tianqingyuluo.onlineim.storage.impl;

import icu.tianqingyuluo.onlineim.storage.OSSAdapter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Amazon S3对象存储适配器实现
 */
@Slf4j
public class S3Adapter implements OSSAdapter {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String endpoint;
    private final Region region;

    /**
     * 构造函数
     *
     * @param endpoint S3服务端点URL，如：https://s3.amazonaws.com
     * @param region 区域，如：us-east-1
     * @param accessKey 访问密钥
     * @param secretKey 秘密密钥
     */
    public S3Adapter(String endpoint, String region, String accessKey, String secretKey) {
        this.endpoint = endpoint;
        this.region = Region.of(region);
        
        // 创建凭证提供者
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(awsCredentials);
        
        // 创建S3客户端
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(this.region)
                .credentialsProvider(credentialsProvider)
                .build();
        
        // 创建S3预签名客户端
        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(this.region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Override
    public String uploadFile(String bucketName, String objectName, InputStream inputStream, String contentType, long size) throws Exception {
        try {
            // 确保存储桶存在
            createBucketIfNotExists(bucketName);
            
            // 创建上传请求
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(contentType)
                    .build();
            
            // 上传文件
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, size));
            
            // 返回文件访问URL
            return getFileUrl(bucketName, objectName);
        } catch (Exception e) {
            log.error("S3上传文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public InputStream downloadFile(String bucketName, String objectName) throws Exception {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest);
            return response;
        } catch (Exception e) {
            log.error("S3下载文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void deleteFile(String bucketName, String objectName) throws Exception {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            
            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            log.error("S3删除文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void deleteFiles(String bucketName, List<String> objectNames) throws Exception {
        try {
            List<ObjectIdentifier> keys = new ArrayList<>();
            for (String objectName : objectNames) {
                keys.add(ObjectIdentifier.builder().key(objectName).build());
            }
            
            Delete delete = Delete.builder()
                    .objects(keys)
                    .build();
            
            DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(delete)
                    .build();
            
            DeleteObjectsResponse response = s3Client.deleteObjects(deleteObjectsRequest);
            
            // 检查是否有删除失败的对象
            if (response.hasErrors()) {
                for (S3Error error : response.errors()) {
                    log.error("S3批量删除文件失败: 对象 {} 删除失败，错误: {}", error.key(), error.message());
                }
            }
        } catch (Exception e) {
            log.error("S3批量删除文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public String getPresignedUrl(String bucketName, String objectName, int expirationTimeSeconds) throws Exception {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationTimeSeconds))
                    .getObjectRequest(getObjectRequest)
                    .build();
            
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("S3获取预签名URL失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public boolean doesObjectExist(String bucketName, String objectName) throws Exception {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            
            s3Client.headObject(headObjectRequest);
            return true;
        } catch (S3Exception e) {
            if (e instanceof NoSuchKeyException || e.statusCode() == 404) {
                return false;
            }
            log.error("S3检查文件是否存在失败: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("S3检查文件是否存在失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void createBucketIfNotExists(String bucketName) throws Exception {
        try {
            // 检查存储桶是否存在
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            try {
                s3Client.headBucket(headBucketRequest);
            } catch (NoSuchBucketException e) {
                // 存储桶不存在，创建新的存储桶
                CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                
                s3Client.createBucket(createBucketRequest);
                log.info("创建S3存储桶成功: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("S3创建存储桶失败: {}", e.getMessage(), e);
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