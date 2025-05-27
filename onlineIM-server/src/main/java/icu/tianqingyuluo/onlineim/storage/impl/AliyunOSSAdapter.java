package icu.tianqingyuluo.onlineim.storage.impl;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.*;
import icu.tianqingyuluo.onlineim.storage.OSSAdapter;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 阿里云OSS对象存储适配器实现
 */
@Slf4j
public class AliyunOSSAdapter implements OSSAdapter {

    private final OSS ossClient;
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String cdnDomain;

    /**
     * 构造函数
     *
     * @param endpoint OSS服务端点URL，如：https://oss-cn-hangzhou.aliyuncs.com
     * @param accessKeyId 访问密钥ID
     * @param accessKeySecret 访问密钥密钥
     * @param cdnDomain 可选的CDN域名，如果提供，则返回的URL将使用此域名
     */
    public AliyunOSSAdapter(String endpoint, String accessKeyId, String accessKeySecret, String cdnDomain) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.cdnDomain = cdnDomain;
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    /**
     * 构造函数（不使用CDN）
     *
     * @param endpoint OSS服务端点URL
     * @param accessKeyId 访问密钥ID
     * @param accessKeySecret 访问密钥密钥
     */
    public AliyunOSSAdapter(String endpoint, String accessKeyId, String accessKeySecret) {
        this(endpoint, accessKeyId, accessKeySecret, null);
    }

    @Override
    public String uploadFile(String bucketName, String objectName, InputStream inputStream, String contentType, long size) throws Exception {
        try {
            // 确保存储桶存在
            createBucketIfNotExists(bucketName);
            
            // 创建上传请求
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(size);
            
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, inputStream, metadata);
            
            // 上传文件
            ossClient.putObject(putObjectRequest);
            
            // 返回文件访问URL
            return getFileUrl(bucketName, objectName);
        } catch (OSSException | ClientException e) {
            log.error("阿里云OSS上传文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public InputStream downloadFile(String bucketName, String objectName) throws Exception {
        try {
            OSSObject ossObject = ossClient.getObject(bucketName, objectName);
            return ossObject.getObjectContent();
        } catch (OSSException | ClientException e) {
            log.error("阿里云OSS下载文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void deleteFile(String bucketName, String objectName) throws Exception {
        try {
            ossClient.deleteObject(bucketName, objectName);
        } catch (OSSException | ClientException e) {
            log.error("阿里云OSS删除文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void deleteFiles(String bucketName, List<String> objectNames) throws Exception {
        try {
            // 阿里云OSS每次最多支持删除1000个文件
            int maxKeysPerBatch = 1000;
            int totalSize = objectNames.size();
            
            for (int i = 0; i < totalSize; i += maxKeysPerBatch) {
                List<String> batchKeys = objectNames.subList(i, Math.min(i + maxKeysPerBatch, totalSize));
                DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(bucketName);
                deleteRequest.setKeys(new ArrayList<>(batchKeys));
                
                DeleteObjectsResult deleteResult = ossClient.deleteObjects(deleteRequest);
                List<String> deletedObjects = deleteResult.getDeletedObjects();
                
                // 检查是否所有对象都已删除
                if (deletedObjects.size() < batchKeys.size()) {
                    log.warn("阿里云OSS批量删除文件部分失败: 请求删除 {} 个对象，实际删除 {} 个对象", 
                            batchKeys.size(), deletedObjects.size());
                }
            }
        } catch (OSSException | ClientException e) {
            log.error("阿里云OSS批量删除文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public String getPresignedUrl(String bucketName, String objectName, int expirationTimeSeconds) throws Exception {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expirationTimeSeconds * 1000L);
            URL url = ossClient.generatePresignedUrl(bucketName, objectName, expiration);
            return url.toString();
        } catch (OSSException | ClientException e) {
            log.error("阿里云OSS获取预签名URL失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public boolean doesObjectExist(String bucketName, String objectName) throws Exception {
        try {
            return ossClient.doesObjectExist(bucketName, objectName);
        } catch (OSSException | ClientException e) {
            log.error("阿里云OSS检查文件是否存在失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void createBucketIfNotExists(String bucketName) throws Exception {
        try {
            if (!ossClient.doesBucketExist(bucketName)) {
                // 创建存储桶
                CreateBucketRequest createBucketRequest = new CreateBucketRequest(bucketName);
                // 设置存储桶权限为公共读，私有写
                createBucketRequest.setCannedACL(CannedAccessControlList.PublicRead);
                ossClient.createBucket(createBucketRequest);
                log.info("创建阿里云OSS存储桶成功: {}", bucketName);
            }
        } catch (OSSException | ClientException e) {
            log.error("阿里云OSS创建存储桶失败: {}", e.getMessage(), e);
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
        if (cdnDomain != null && !cdnDomain.isEmpty()) {
            // 使用CDN域名
            return String.format("%s/%s", cdnDomain, objectName);
        } else {
            // 使用OSS默认域名
            return String.format("https://%s.%s/%s", bucketName, endpoint.replace("https://", "").replace("http://", ""), objectName);
        }
    }
    
    /**
     * 关闭OSS客户端
     */
    public void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}