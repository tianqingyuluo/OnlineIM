package icu.tianqingyuluo.onlineim.storage.impl;

/**
 * RustFS 对象存储适配器实现。
 * RustFS 提供 S3 兼容接口，这里复用 MinIO Java SDK 调用链。
 */
public class RustFSAdapter extends MinIOAdapter {

    public RustFSAdapter(String endpoint, String accessKey, String secretKey) {
        super("RustFS", endpoint, accessKey, secretKey);
    }
}
