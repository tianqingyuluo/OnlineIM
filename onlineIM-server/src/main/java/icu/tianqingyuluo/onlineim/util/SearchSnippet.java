package icu.tianqingyuluo.onlineim.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息搜索的命中片段：在文本里找出关键字（忽略大小写）的所有位置，截取第一个命中附近的一段，
 * 返回片段文本与片段内每个命中的 {start, length}（UTF-16 下标，与浏览器 JS 字符串一致）。
 * 前端按下标拆分渲染高亮，不需要 v-html。
 */
public final class SearchSnippet {
    /** 第一个命中前保留的字符数 */
    public static final int CONTEXT_BEFORE = 16;
    /** 片段最长字符数（不含省略号） */
    public static final int MAX_LENGTH = 80;
    public static final String ELLIPSIS = "…";

    private SearchSnippet() {
    }

    public record Highlight(int start, int length) {
    }

    public record Result(String snippet, List<Highlight> highlights) {
    }

    /** 关键字在 text 中的所有不重叠位置（忽略大小写，不改变字符串长度） */
    public static List<Integer> matchPositions(String text, String keyword) {
        List<Integer> positions = new ArrayList<>();
        if (text == null || keyword == null || keyword.isEmpty()) {
            return positions;
        }
        int length = keyword.length();
        int index = 0;
        while (index + length <= text.length()) {
            if (text.regionMatches(true, index, keyword, 0, length)) {
                positions.add(index);
                index += length;
            } else {
                index++;
            }
        }
        return positions;
    }

    public static Result build(String content, String keyword) {
        String text = content == null ? "" : content.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        List<Integer> positions = matchPositions(text, keyword);
        if (positions.isEmpty()) {
            int end = safeEnd(text, Math.min(text.length(), MAX_LENGTH));
            return new Result(end < text.length() ? text.substring(0, end) + ELLIPSIS : text, List.of());
        }
        int keywordLength = keyword.length();
        int first = positions.getFirst();
        int start = safeStart(text, Math.max(0, first - CONTEXT_BEFORE));
        int end = Math.max(Math.min(text.length(), start + MAX_LENGTH), Math.min(text.length(), first + keywordLength));
        end = safeEnd(text, end);
        String prefix = start > 0 ? ELLIPSIS : "";
        String suffix = end < text.length() ? ELLIPSIS : "";
        List<Highlight> highlights = new ArrayList<>();
        for (int position : positions) {
            if (position >= start && position + keywordLength <= end) {
                highlights.add(new Highlight(prefix.length() + position - start, keywordLength));
            }
        }
        return new Result(prefix + text.substring(start, end) + suffix, highlights);
    }

    /** 不从代理对的低位开始截取 */
    private static int safeStart(String text, int start) {
        if (start > 0 && start < text.length() && Character.isLowSurrogate(text.charAt(start))) {
            return start - 1;
        }
        return start;
    }

    /** 不在代理对中间截断 */
    private static int safeEnd(String text, int end) {
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
            return end + 1;
        }
        return end;
    }
}
