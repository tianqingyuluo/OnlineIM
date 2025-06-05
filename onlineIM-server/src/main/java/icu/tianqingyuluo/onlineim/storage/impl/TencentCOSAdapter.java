package icu.tianqingyuluo.onlineim.storage.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import icu.tianqingyuluo.onlineim.storage.OSSAdapter;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 腾讯云COS对象存储适配器实现
 */
@Slf4j
public class TencentCOSAdapter implements OSSAdapter {

    private final COSClient cosClient;
    private final String bucketRegion;
    private final String secretId;
    private final String secretKey;
    private final String cdnDomain;

    /**
     * 构造函数
     *
     * @param region 区域，如：ap-guangzhou
     * @param secretId 密钥ID
     * @param secretKey 密钥Key
     * @param cdnDomain 可选的CDN域名，如果提供，则返回的URL将使用此域名
     */
    public TencentCOSAdapter(String region, String secretId, String secretKey, String cdnDomain) {
        this.bucketRegion = region;
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.cdnDomain = cdnDomain;
        
        // 初始化COS客户端
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        // 设置使用HTTPS
        clientConfig.setHttpProtocol(HttpProtocol.https);
        
        this.cosClient = new COSClient(cred, clientConfig);
    }

    /**
     * 构造函数（不使用CDN）
     *
     * @param region 区域
     * @param secretId 密钥ID
     * @param secretKey 密钥Key
     */
    public TencentCOSAdapter(String region, String secretId, String secretKey) {
        this(region, secretId, secretKey, null);
    }

    @Override
    public String uploadFile(String bucketName, String objectName, InputStream inputStream, String contentType, long size) throws Exception {
        try {
            // 确保存储桶存在
            createBucketIfNotExists(bucketName);
            
            // 设置上传对象的元数据
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(size);
            
            // 创建上传请求
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, inputStream, metadata);
            
            // 上传文件
            cosClient.putObject(putObjectRequest);
            
            // 返回文件访问URL
            return getFileUrl(bucketName, objectName);
        } catch (CosClientException e) {
            log.error("腾讯云COS上传文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public InputStream downloadFile(String bucketName, String objectName) throws Exception {
        try {
            GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectName);
            COSObject cosObject = cosClient.getObject(getObjectRequest);
            return cosObject.getObjectContent();
        } catch (CosClientException e) {
            log.error("腾讯云COS下载文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void deleteFile(String bucketName, String objectName) throws Exception {
        try {
            cosClient.deleteObject(bucketName, objectName);
        } catch (CosClientException e) {
            log.error("腾讯云COS删除文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void deleteFiles(String bucketName, List<String> objectNames) throws Exception {
        try {
            // 腾讯云COS每次最多支持删除1000个文件
            int maxKeysPerBatch = 1000;
            int totalSize = objectNames.size();
            
            for (int i = 0; i < totalSize; i += maxKeysPerBatch) {
                List<String> batchKeys = objectNames.subList(i, Math.min(i + maxKeysPerBatch, totalSize));
                
                // 构建批量删除请求
                DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName);
                List<DeleteObjectsRequest.KeyVersion> keyVersions = new ArrayList<>();
                
                for (String objectName : batchKeys) {
                    keyVersions.add(new DeleteObjectsRequest.KeyVersion(objectName));
                }
                
                deleteObjectsRequest.setKeys(keyVersions);
                
                // 执行批量删除
                DeleteObjectsResult deleteResult = cosClient.deleteObjects(deleteObjectsRequest);
                
                // 检查是否有删除失败的对象
                if (deleteResult.getDeletedObjects().size() < batchKeys.size()) {
                    log.warn("腾讯云COS批量删除文件部分失败: 请求删除 {} 个对象，实际删除 {} 个对象", 
                            batchKeys.size(), deleteResult.getDeletedObjects().size());
                }
            }
        } catch (CosClientException e) {
            log.error("腾讯云COS批量删除文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public String getPresignedUrl(String bucketName, String objectName, int expirationTimeSeconds) throws Exception {
        try {
            // 设置签名过期时间
            Date expirationDate = new Date(System.currentTimeMillis() + expirationTimeSeconds * 1000L);
            
            // 生成预签名URL
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethodName.GET);
            request.setExpiration(expirationDate);
            
            URL url = cosClient.generatePresignedUrl(request);
            return url.toString();
        } catch (CosClientException e) {
            log.error("腾讯云COS获取预签名URL失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public boolean doesObjectExist(String bucketName, String objectName) throws Exception {
        try {
            return cosClient.doesObjectExist(bucketName, objectName);
        } catch (CosClientException e) {
            log.error("腾讯云COS检查文件是否存在失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void createBucketIfNotExists(String bucketName) throws Exception {
        try {
            if (!cosClient.doesBucketExist(bucketName)) {
                // 创建存储桶
                CreateBucketRequest createBucketRequest = new CreateBucketRequest(bucketName);
                // 设置存储桶权限为公共读，私有写
                createBucketRequest.setCannedAcl(CannedAccessControlList.PublicRead);
                cosClient.createBucket(createBucketRequest);
                log.info("创建腾讯云COS存储桶成功: {}", bucketName);
            }
        } catch (CosClientException e) {
            log.error("腾讯云COS创建存储桶失败: {}", e.getMessage(), e);
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
            // 使用COS默认域名
            return String.format("https://%s.cos.%s.myqcloud.com/%s", bucketName, bucketRegion, objectName);
        }
    }
    
    /**
     * 关闭COS客户端
     */
    public void shutdown() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }
}