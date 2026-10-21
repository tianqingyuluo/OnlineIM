package icu.tianqingyuluo.onlineim.util;

/**
 * 会话列表里的草稿摘要：折叠空白，按 Unicode code point 截断。
 * 客户端 {@code draftPreview} 使用同一规则，刷新前后显示一致。
 */
public final class DraftPreview {

    public static final int MAX_LENGTH = 40;

    private DraftPreview() {
    }

    public static String of(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String collapsed = content.replaceAll("\\s+", " ").trim();
        int count = collapsed.codePointCount(0, collapsed.length());
        if (count <= MAX_LENGTH) {
            return collapsed;
        }
        int end = collapsed.offsetByCodePoints(0, MAX_LENGTH);
        return collapsed.substring(0, end) + "…";
    }
}
