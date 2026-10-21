package icu.tianqingyuluo.onlineim.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MentionUtilTest {

    private static final List<String> MEMBERS = List.of("usr_a", "usr_b", "usr_c");

    @Test
    void keepsOnlyDistinctCurrentMembersExceptSender() {
        assertEquals(List.of("usr_b", "usr_c"), MentionUtil.sanitize("text", "usr_a",
                Arrays.asList("usr_b", "usr_b", null, "", "usr_a", "usr_outsider", "all", "usr_c"), MEMBERS));
    }

    @Test
    void returnsNullWhenNothingValidOrNotText() {
        assertNull(MentionUtil.sanitize("text", "usr_a", List.of("usr_x", "usr_a"), MEMBERS));
        assertNull(MentionUtil.sanitize("text", "usr_a", List.of(), MEMBERS));
        assertNull(MentionUtil.sanitize("text", "usr_a", null, MEMBERS));
        assertNull(MentionUtil.sanitize("image", "usr_a", List.of("usr_b"), MEMBERS));
        assertNull(MentionUtil.sanitize("emoji", "usr_a", List.of("usr_b"), MEMBERS));
        assertNull(MentionUtil.sanitize("text", "usr_a", List.of("usr_b"), null));
    }

    @Test
    void capsTheNumberOfMentions() {
        List<String> members = new ArrayList<>(IntStream.range(0, 80).mapToObj(i -> "usr_" + i).toList());
        List<String> result = MentionUtil.sanitize("text", "usr_me", members, members);
        assertEquals(MentionUtil.MAX_MENTIONS, result.size());
        assertEquals("usr_0", result.getFirst());
    }

    @Test
    void keepsAtAllForManagersWithoutUsingThePersonCap() {
        List<String> members = new ArrayList<>(IntStream.range(0, 60).mapToObj(i -> "usr_" + i).toList());
        members.add("usr_me");
        List<String> requested = new ArrayList<>();
        requested.add("all");
        requested.add("usr_me");
        requested.add("usr_outsider");
        requested.addAll(members);

        List<String> result = MentionUtil.sanitize("text", "usr_me", requested, members, true);

        assertEquals(MentionUtil.MENTION_ALL, result.getFirst());
        assertEquals(MentionUtil.MAX_MENTIONS + 1, result.size());
        assertFalse(result.contains("usr_me"));
        assertEquals(java.util.Set.copyOf(MentionUtil.notifiedUserIds("usr_me", result, members)).size(), members.size() - 1);
    }

    @Test
    void dropsAtAllWhenTheSenderIsNotAManager() {
        assertEquals(List.of("usr_b"), MentionUtil.sanitize("text", "usr_a",
                List.of("all", "usr_b"), MEMBERS, false));
        assertNull(MentionUtil.sanitize("text", "usr_a", List.of("all"), MEMBERS, false));
        assertEquals(List.of("all"), MentionUtil.sanitize("text", "usr_a", List.of("all", "usr_a"), MEMBERS, true));
    }
}
