package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传当前用户的公钥包。请求里如果夹带私钥字段，会被忽略，不会落库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class E2eeKeyUploadRequest {

    @NotBlank(message = "身份公钥不能为空")
    @JsonProperty("identity_public_key")
    @JsonAlias("identityPublicKey")
    private String identityPublicKey;

    @NotBlank(message = "签名公钥不能为空")
    @JsonProperty("signing_public_key")
    @JsonAlias("signingPublicKey")
    private String signingPublicKey;

    @NotBlank(message = "密钥标识不能为空")
    @JsonProperty("key_id")
    @JsonAlias("keyId")
    private String keyId;

    @NotBlank(message = "算法不能为空")
    private String algorithm;

    @NotBlank(message = "指纹不能为空")
    private String fingerprint;
}
