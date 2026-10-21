package icu.tianqingyuluo.onlineim.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在当前 MySQL 事务提交后再执行（没有事务时立即执行），
 * 用于跟随成员关系变化维护 MongoDB 中的会话记录，避免事务回滚后留下孤儿记录。
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
