package icu.tianqingyuluo.onlineim.storage;

import java.io.InputStream;
import java.util.List;

/**
 * 对象存储服务适配器接口
 * 提供统一的接口用于连接不同的对象存储服务（MinIO、S3、阿里云OSS、腾讯云COS等）
 */
public interface OSSAdapter {
    
    /**
     * 上传文件到对象存储
     * 
     * @param bucketName 存储桶名称
     * @param objectName 对象名称，通常包含路径信息，如：images/avatar/user123.jpg
     * @param inputStream 文件输入流
     * @param contentType 文件内容类型，如：image/jpeg
     * @param size 文件大小（字节）
     * @return 访问URL
     * @throws Exception 上传过程中的异常
     */
    String uploadFile(String bucketName, String objectName, InputStream inputStream, String contentType, long size) throws Exception;
    
    /**
     * 下载文件
     * 
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 文件输入流
     * @throws Exception 下载过程中的异常
     */
    InputStream downloadFile(String bucketName, String objectName) throws Exception;
    
    /**
     * 删除文件
     * 
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @throws Exception 删除过程中的异常
     */
    void deleteFile(String bucketName, String objectName) throws Exception;
    
    /**
     * 批量删除文件
     * 
     * @param bucketName 存储桶名称
     * @param objectNames 对象名称列表
     * @throws Exception 删除过程中的异常
     */
    void deleteFiles(String bucketName, List<String> objectNames) throws Exception;
    
    /**
     * 获取文件的临时访问URL
     * 
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @param expirationTimeSeconds URL有效期（秒）
     * @return 临时访问URL
     * @throws Exception 获取URL过程中的异常
     */
    String getPresignedUrl(String bucketName, String objectName, int expirationTimeSeconds) throws Exception;
    
    /**
     * 检查文件是否存在
     * 
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 文件是否存在
     * @throws Exception 检查过程中的异常
     */
    boolean doesObjectExist(String bucketName, String objectName) throws Exception;
    
    /**
     * 创建存储桶（如果不存在）
     * 
     * @param bucketName 存储桶名称
     * @throws Exception 创建过程中的异常
     */
    void createBucketIfNotExists(String bucketName) throws Exception;
}