package icu.tianqingyuluo.onlineim.util;

import java.math.BigInteger;

/**
 * 消息 seqId 的统一比较工具。
 *
 * <p>消息文档和 WebSocket 协议目前以字符串传输 Snowflake 序列号，不能使用字符串字典序或
 * 数值相减判断顺序。所有回执游标更新都必须经过本工具。</p>
 */
public final class SeqIdComparator {

    private static final String ZERO = "0";

    private SeqIdComparator() {
    }

    public static int compare(String left, String right) {
        return parse(left).compareTo(parse(right));
    }

    public static String max(String left, String right) {
        String normalizedLeft = normalizeNullable(left);
        String normalizedRight = normalizeNullable(right);
        return compare(normalizedLeft, normalizedRight) >= 0 ? normalizedLeft : normalizedRight;
    }

    public static String requireValid(String seqId) {
        return parse(seqId).toString();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? ZERO : value;
    }

    private static BigInteger parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("seqId 不能为空");
        }
        try {
            BigInteger parsed = new BigInteger(value);
            if (parsed.signum() < 0) {
                throw new IllegalArgumentException("seqId 不能为负数");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("seqId 必须是非负整数", ex);
        }
    }
}
