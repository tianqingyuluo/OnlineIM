package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class E2eeKeyResponse {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("key_id")
    private String keyId;

    @JsonProperty("identity_public_key")
    private String identityPublicKey;

    @JsonProperty("signing_public_key")
    private String signingPublicKey;

    private String algorithm;

    private String fingerprint;

    @JsonProperty("updated_at")
    private Long updatedAt;
}
