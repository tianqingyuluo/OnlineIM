package icu.tianqingyuluo.onlineim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeqIdComparatorTest {

    @Test
    void comparesSnowflakeIdsNumericallyRatherThanLexicographically() {
        assertTrue(SeqIdComparator.compare("999999999999999999", "1000000000000000000") < 0);
        assertTrue(SeqIdComparator.compare("1000000000000000000", "999999999999999999") > 0);
    }

    @Test
    void maxReturnsTheHigherSequenceAndTreatsNullAsZero() {
        assertEquals("100", SeqIdComparator.max("9", "100"));
        assertEquals("100", SeqIdComparator.max("100", "9"));
        assertEquals("9", SeqIdComparator.max(null, "9"));
        assertEquals("9", SeqIdComparator.max("9", null));
    }

    @Test
    void rejectsBlankOrNonNumericSequenceIds() {
        assertThrows(IllegalArgumentException.class, () -> SeqIdComparator.requireValid(""));
        assertThrows(IllegalArgumentException.class, () -> SeqIdComparator.requireValid("not-a-number"));
        assertDoesNotThrow(() -> SeqIdComparator.requireValid("0"));
    }
}
