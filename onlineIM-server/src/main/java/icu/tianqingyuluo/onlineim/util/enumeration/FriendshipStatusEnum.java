package icu.tianqingyuluo.onlineim.util.enumeration;

import lombok.Getter;

public enum FriendshipStatusEnum {
    NORMAL(1),
    BLOCKED(2);

    @Getter
    private final int value;
    FriendshipStatusEnum(int value) {
        this.value = value;
    }
}
