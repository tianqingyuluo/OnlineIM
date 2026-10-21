package icu.tianqingyuluo.onlineim.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchSnippetTest {

    @Test
    void shortTextKeepsWholeContentAndMarksEveryHitIgnoringCase() {
        SearchSnippet.Result result = SearchSnippet.build("Hello 世界，hello again", "HELLO");
        assertEquals("Hello 世界，hello again", result.snippet());
        assertEquals(List.of(new SearchSnippet.Highlight(0, 5), new SearchSnippet.Highlight(9, 5)), result.highlights());
    }

    @Test
    void longTextIsCutAroundFirstHitWithEllipsisAndOffsetsIncludePrefix() {
        String text = "前".repeat(40) + "关键字" + "后".repeat(100) + "关键字";
        SearchSnippet.Result result = SearchSnippet.build(text, "关键字");
        assertTrue(result.snippet().startsWith(SearchSnippet.ELLIPSIS + "前".repeat(SearchSnippet.CONTEXT_BEFORE) + "关键字"));
        assertTrue(result.snippet().endsWith(SearchSnippet.ELLIPSIS));
        assertEquals(1 + SearchSnippet.MAX_LENGTH + 1, result.snippet().length());
        // 第二个命中在片段之外，不标记
        assertEquals(List.of(new SearchSnippet.Highlight(1 + SearchSnippet.CONTEXT_BEFORE, 3)), result.highlights());
        assertEquals("关键字", result.snippet().substring(17, 20));
    }

    @Test
    void newlinesBecomeSpacesAndHitsDoNotOverlap() {
        SearchSnippet.Result result = SearchSnippet.build("aaa\nb", "aa");
        assertEquals("aaa b", result.snippet());
        assertEquals(List.of(new SearchSnippet.Highlight(0, 2)), result.highlights());
    }

    @Test
    void doesNotSplitSurrogatePairsAtEdges() {
        String emoji = "😀";
        // 第一个命中前 16 个字符处恰好是代理对的低位
        String text = "x" + emoji.repeat(20) + "y" + "目标" + "z".repeat(100);
        assertTrue(Character.isLowSurrogate(text.charAt(text.indexOf("目标") - SearchSnippet.CONTEXT_BEFORE)));
        SearchSnippet.Result result = SearchSnippet.build(text, "目标");
        String body = result.snippet().substring(1);
        assertTrue(!Character.isLowSurrogate(body.charAt(0)), "片段不能从代理对低位开始");
        int hit = result.highlights().getFirst().start();
        assertEquals("目标", result.snippet().substring(hit, hit + 2));
    }

    @Test
    void noHitFallsBackToLeadingText() {
        SearchSnippet.Result result = SearchSnippet.build("z".repeat(100), "q");
        assertEquals("z".repeat(SearchSnippet.MAX_LENGTH) + SearchSnippet.ELLIPSIS, result.snippet());
        assertTrue(result.highlights().isEmpty());
    }
}
