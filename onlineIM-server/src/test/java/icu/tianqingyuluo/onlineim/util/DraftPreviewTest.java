package icu.tianqingyuluo.onlineim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DraftPreviewTest {

    @Test
    void collapsesWhitespaceAndTruncatesByCodePoint() {
        assertEquals("", DraftPreview.of(null));
        assertEquals("", DraftPreview.of("   \n"));
        assertEquals("你好 世界", DraftPreview.of("  你好\n\n世界  "));
        String longText = "哈".repeat(41);
        assertEquals("哈".repeat(40) + "…", DraftPreview.of(longText));
        assertEquals("🙂".repeat(40) + "…", DraftPreview.of("🙂".repeat(45)));
    }
}
