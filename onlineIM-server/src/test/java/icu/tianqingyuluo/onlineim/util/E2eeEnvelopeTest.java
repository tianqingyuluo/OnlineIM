package icu.tianqingyuluo.onlineim.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class E2eeEnvelopeTest {

    private static final String ENVELOPE = """
            {"e2ee":true,"version":1,"ciphertext":"AAAA","iv":"BBBB","sender_key_id":"key_a","recipient_key_id":"key_b"}
            """;

    @Test
    void detectsFlagOrEnvelope() {
        assertTrue(E2eeEnvelope.isEncrypted(true, "你好"));
        assertTrue(E2eeEnvelope.isEncrypted(null, ENVELOPE));
        assertTrue(E2eeEnvelope.looksLikeEnvelope(ENVELOPE));
        assertFalse(E2eeEnvelope.isEncrypted(false, "明天去爬山"));
        assertFalse(E2eeEnvelope.isEncrypted(null, "提到 ciphertext 但不是信封"));
        assertFalse(E2eeEnvelope.looksLikeEnvelope("{\"ciphertext\":\"only\"}"));
    }

    @Test
    void searchCriteriaExcludesEncryptedMessages() {
        Criteria criteria = E2eeEnvelope.notEncryptedCriteria();
        String rendered = criteria.getCriteriaObject().toJson();
        assertTrue(rendered.contains("isEncrypted"));
        assertTrue(rendered.contains("ciphertext"));
    }
}
