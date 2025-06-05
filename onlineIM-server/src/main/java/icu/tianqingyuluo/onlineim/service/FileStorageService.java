package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.storage.OSSAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * 文件存储服务
 * 封装OSS适配器的操作，提供更高级的文件存储功能
 */
@Slf4j
@Service
public class FileStorageService {

    private final OSSAdapter ossAdapter;
    
    @Value("${oss.default-bucket:default}")
    private String defaultBucket;
    
    @Autowired
    public FileStorageService(OSSAdapter ossAdapter) {
        this.ossAdapter = ossAdapter;
    }
    
    /**
     * 上传文件
     * 
     * @param file 文件对象
     * @param directory 目录路径，如：images/avatar
     * @return 文件访问URL
     * @throws Exception 上传过程中的异常
     */
    public String uploadFile(MultipartFile file, String directory) throws Exception {
        return uploadFile(file, defaultBucket, directory);
    }
    
    /**
     * 上传文件到指定存储桶
     * 
     * @param file 文件对象
     * @param bucketName 存储桶名称
     * @param directory 目录路径，如：images/avatar
     * @return 文件访问URL
     * @throws Exception 上传过程中的异常
     */
    public String uploadFile(MultipartFile file, String bucketName, String directory) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String objectName = generateObjectName(directory, extension);
        
        try (InputStream inputStream = file.getInputStream()) {
            return ossAdapter.uploadFile(bucketName, objectName, inputStream, file.getContentType(), file.getSize());
        } catch (Exception e) {
            log.error("上传文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 上传头像
     * 
     * @param file 头像文件
     * @param userId 用户ID
     * @return 头像访问URL
     * @throws Exception 上传过程中的异常
     */
    public String uploadAvatar(MultipartFile file, String userId) throws Exception {
        String directory = "avatars/" + userId;
        return uploadFile(file, directory);
    }
    
    /**
     * 上传群组头像
     * 
     * @param file 头像文件
     * @param groupId 群组ID
     * @return 头像访问URL
     * @throws Exception 上传过程中的异常
     */
    public String uploadGroupAvatar(MultipartFile file, String groupId) throws Exception {
        String directory = "groups/avatars/" + groupId;
        return uploadFile(file, directory);
    }
    
    /**
     * 上传聊天图片
     * 
     * @param file 图片文件
     * @param conversationId 会话ID
     * @return 图片访问URL
     * @throws Exception 上传过程中的异常
     */
    public String uploadChatImage(MultipartFile file, String conversationId) throws Exception {
        String directory = "chats/images/" + conversationId;
        return uploadFile(file, directory);
    }
    
    /**
     * 上传聊天文件
     * 
     * @param file 文件对象
     * @param conversationId 会话ID
     * @return 文件访问URL
     * @throws Exception 上传过程中的异常
     */
    public String uploadChatFile(MultipartFile file, String conversationId) throws Exception {
        String directory = "chats/files/" + conversationId;
        return uploadFile(file, directory);
    }
    
    /**
     * 上传聊天语音
     * 
     * @param file 语音文件
     * @param conversationId 会话ID
     * @return 语音访问URL
     * @throws Exception 上传过程中的异常
     */
    public String uploadChatVoice(MultipartFile file, String conversationId) throws Exception {
        String directory = "chats/voices/" + conversationId;
        return uploadFile(file, directory);
    }
    
    /**
     * 上传聊天视频
     * 
     * @param file 视频文件
     * @param conversationId 会话ID
     * @return 视频访问URL
     * @throws Exception 上传过程中的异常
     */
    public String uploadChatVideo(MultipartFile file, String conversationId) throws Exception {
        String directory = "chats/videos/" + conversationId;
        return uploadFile(file, directory);
    }
    
    /**
     * 下载文件
     * 
     * @param objectName 对象名称
     * @return 文件输入流
     * @throws Exception 下载过程中的异常
     */
    public InputStream downloadFile(String objectName) throws Exception {
        return downloadFile(defaultBucket, objectName);
    }
    
    /**
     * 从指定存储桶下载文件
     * 
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 文件输入流
     * @throws Exception 下载过程中的异常
     */
    public InputStream downloadFile(String bucketName, String objectName) throws Exception {
        try {
            return ossAdapter.downloadFile(bucketName, objectName);
        } catch (Exception e) {
            log.error("下载文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 删除文件
     * 
     * @param objectName 对象名称
     * @throws Exception 删除过程中的异常
     */
    public void deleteFile(String objectName) throws Exception {
        deleteFile(defaultBucket, objectName);
    }
    
    /**
     * 从指定存储桶删除文件
     * 
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @throws Exception 删除过程中的异常
     */
    public void deleteFile(String bucketName, String objectName) throws Exception {
        try {
            ossAdapter.deleteFile(bucketName, objectName);
        } catch (Exception e) {
            log.error("删除文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 批量删除文件
     * 
     * @param objectNames 对象名称列表
     * @throws Exception 删除过程中的异常
     */
    public void deleteFiles(List<String> objectNames) throws Exception {
        deleteFiles(defaultBucket, objectNames);
    }
    
    /**
     * 从指定存储桶批量删除文件
     * 
     * @param bucketName 存储桶名称
     * @param objectNames 对象名称列表
     * @throws Exception 删除过程中的异常
     */
    public void deleteFiles(String bucketName, List<String> objectNames) throws Exception {
        try {
            ossAdapter.deleteFiles(bucketName, objectNames);
        } catch (Exception e) {
            log.error("批量删除文件失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 获取文件的临时访问URL
     * 
     * @param objectName 对象名称
     * @param expirationTimeSeconds URL有效期（秒）
     * @return 临时访问URL
     * @throws Exception 获取URL过程中的异常
     */
    public String getPresignedUrl(String objectName, int expirationTimeSeconds) throws Exception {
        return getPresignedUrl(defaultBucket, objectName, expirationTimeSeconds);
    }
    
    /**
     * 获取指定存储桶中文件的临时访问URL
     * 
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @param expirationTimeSeconds URL有效期（秒）
     * @return 临时访问URL
     * @throws Exception 获取URL过程中的异常
     */
    public String getPresignedUrl(String bucketName, String objectName, int expirationTimeSeconds) throws Exception {
        try {
            return ossAdapter.getPresignedUrl(bucketName, objectName, expirationTimeSeconds);
        } catch (Exception e) {
            log.error("获取预签名URL失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 检查文件是否存在
     * 
     * @param objectName 对象名称
     * @return 文件是否存在
     * @throws Exception 检查过程中的异常
     */
    public boolean doesObjectExist(String objectName) throws Exception {
        return doesObjectExist(defaultBucket, objectName);
    }
    
    /**
     * 检查指定存储桶中的文件是否存在
     * 
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 文件是否存在
     * @throws Exception 检查过程中的异常
     */
    public boolean doesObjectExist(String bucketName, String objectName) throws Exception {
        try {
            return ossAdapter.doesObjectExist(bucketName, objectName);
        } catch (Exception e) {
            log.error("检查文件是否存在失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 创建存储桶（如果不存在）
     * 
     * @param bucketName 存储桶名称
     * @throws Exception 创建过程中的异常
     */
    public void createBucketIfNotExists(String bucketName) throws Exception {
        try {
            ossAdapter.createBucketIfNotExists(bucketName);
        } catch (Exception e) {
            log.error("创建存储桶失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 获取文件扩展名
     * 
     * @param filename 文件名
     * @return 文件扩展名（包含点号）
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return filename.substring(dotIndex);
    }
    
    /**
     * 生成对象名称
     * 
     * @param directory 目录路径
     * @param extension 文件扩展名（包含点号）
     * @return 对象名称
     */
    private String generateObjectName(String directory, String extension) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        if (directory == null || directory.isEmpty()) {
            return uuid + extension;
        }
        if (directory.endsWith("/")) {
            return directory + uuid + extension;
        }
        return directory + "/" + uuid + extension;
    }
}