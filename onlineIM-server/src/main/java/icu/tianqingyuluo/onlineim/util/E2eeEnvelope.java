package icu.tianqingyuluo.onlineim.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * 私聊端到端加密的密文信封识别。服务端只判断“这是不是密文”，不解密。
 *
 * <p>信封是放在消息 {@code content} 里的 JSON，至少包含 {@code ciphertext} 与 {@code iv}。
 * 客户端同时会带 {@code is_encrypted=true}。两者任一成立，预览、引用快照和搜索都不得暴露正文。</p>
 */
public final class E2eeEnvelope {

    public static final String PREVIEW = "[加密消息]";
    public static final String ALGORITHM = "ECDH-P256+ECDSA-P256";

    private static final ObjectMapper JSON = new ObjectMapper();

    private E2eeEnvelope() {
    }

    public static boolean isEncrypted(Boolean flag, String content) {
        return Boolean.TRUE.equals(flag) || looksLikeEnvelope(content);
    }

    public static boolean looksLikeEnvelope(String content) {
        if (content == null) {
            return false;
        }
        String trimmed = content.stripLeading();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '{') {
            return false;
        }
        if (!trimmed.contains("\"ciphertext\"") || !trimmed.contains("\"iv\"")) {
            return false;
        }
        try {
            JsonNode node = JSON.readTree(trimmed);
            return node.isObject()
                    && node.hasNonNull("ciphertext")
                    && node.hasNonNull("iv")
                    && !node.get("ciphertext").asText("").isBlank()
                    && !node.get("iv").asText("").isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 搜索条件：排除带加密标记的消息，以及内容本身就是密文信封的消息，
     * 避免 Base64 密文被关键字正则误当成明文命中。
     */
    public static Criteria notEncryptedCriteria() {
        return new Criteria().norOperator(
                Criteria.where("isEncrypted").is(true),
                Criteria.where("is_encrypted").is(true),
                new Criteria().andOperator(
                        Criteria.where("content").regex("\"ciphertext\""),
                        Criteria.where("content").regex("\"iv\"")));
    }
}
