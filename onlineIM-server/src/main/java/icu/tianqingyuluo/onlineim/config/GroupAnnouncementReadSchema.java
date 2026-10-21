package icu.tianqingyuluo.onlineim.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 已有库没有 Flyway。启动时幂等补上群公告已读表，避免只更新了 onlineIM.sql 的环境缺表。
 */
@Slf4j
@Component
public class GroupAnnouncementReadSchema implements InitializingBean {

    static final String DDL = """
            CREATE TABLE IF NOT EXISTS `group_announcement_reads` (
                `id` VARCHAR(143) NOT NULL COMMENT '记录ID，格式为：anr_+snowflake',
                `announcement_id` VARCHAR(143) NOT NULL COMMENT '公告ID，格式为：ann_+snowflake',
                `group_id` VARCHAR(143) NOT NULL COMMENT '群组ID，格式为：grp_+snowflake',
                `user_id` VARCHAR(143) NOT NULL COMMENT '已读用户ID，格式为：usr_+snowflake',
                `read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '确认已读时间',
                PRIMARY KEY (`id`),
                UNIQUE KEY `uk_announcement_user` (`announcement_id`, `user_id`),
                KEY `idx_group_user` (`group_id`, `user_id`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群公告已读'
            """;

    private final DataSource dataSource;

    public GroupAnnouncementReadSchema(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(DDL);
        } catch (Exception ex) {
            log.error("创建群公告已读表失败: {}", ex.getMessage(), ex);
        }
    }
}
