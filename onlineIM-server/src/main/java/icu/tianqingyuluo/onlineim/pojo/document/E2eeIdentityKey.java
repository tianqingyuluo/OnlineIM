package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 用户当前的端到端加密公钥包。只存公钥，私钥永远不上传。
 * 文档 _id 即用户 ID，每个用户只保留一把当前身份密钥。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "e2ee_identity_keys")
public class E2eeIdentityKey {

    @Id
    private String userId;

    /** 客户端生成的密钥标识，换设备导入后保持不变 */
    private String keyId;

    /** ECDH P-256 公钥，SPKI 的标准 Base64 */
    private String identityPublicKey;

    /** ECDSA P-256 公钥，SPKI 的标准 Base64 */
    private String signingPublicKey;

    /** 固定为 ECDH-P256+ECDSA-P256 */
    private String algorithm;

    /** identity 公钥 SPKI 字节的 SHA-256 小写十六进制 */
    private String fingerprint;

    private Date updatedAt;
}
