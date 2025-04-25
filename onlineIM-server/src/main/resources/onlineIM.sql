create database online_im;
use online_im;
CREATE TABLE `user` (
                        `userid` varchar(128) NOT NULL,
                        `username` varchar(20) NOT NULL COMMENT '用户初始取的登录用的唯一用户名',
                        `nickname` varchar(32) NOT NULL COMMENT '用户在聊天界面现实的昵称',
                        `password` varchar(128) NOT NULL,
                        `avatarurl` varchar(256) NOT NULL COMMENT '用户头像URL',
                        `createat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (`userid`),
                        UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `group` (
                         `gid` varchar(128) NOT NULL COMMENT '群ID',
                         `gname` varchar(30) NOT NULL COMMENT '群名称',
                         `gavatar` varchar(255) DEFAULT NULL COMMENT '群头像URL',
                         `ownerid` varchar(128) NOT NULL COMMENT '群主用户ID',
                         `createat` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         `updateat` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         PRIMARY KEY (`gid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='群组基础信息表';

CREATE TABLE `group_member` (
                                `group_member_id` varchar(128) NOT NULL COMMENT '主键ID',
                                `gid` varchar(128) NOT NULL COMMENT '群ID,需要在应用层模拟外键约束',
                                `userid` varchar(128) NOT NULL COMMENT '成员用户ID,同gid',
                                `role` tinyint NOT NULL DEFAULT '0' COMMENT '角色：0-普通成员 1-管理员 2-群主',
                                `group_nickname` varchar(30) COMMENT '可以在群里自定义的名称',
                                `silence_until` datetime DEFAULT '1970-01-01 00:00:01' COMMENT '禁言失效的时间戳',
                                `join_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
                                `last_active_time` datetime DEFAULT NULL COMMENT '最后活跃时间',
                                PRIMARY KEY (`group_member_id`),
                                UNIQUE KEY `uk_group_user` (`gid`,`userid`),
                                KEY `idx_user_id` (`userid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='群成员表';

CREATE TABLE `group_announcement` (
                                      `group_announcement_id` varchar(128) NOT NULL COMMENT '群公告ID',
                                      `gid` varchar(128) NOT NULL COMMENT '所属群ID',
                                      `userid` varchar(128) NOT NULL COMMENT '发布人用户ID',
                                      `title` varchar(50) DEFAULT NULL COMMENT '公告标题（可选）',
                                      `content` text NOT NULL COMMENT '公告内容',
                                      `is_pinned` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否 1-是',
                                      `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0-已删除 1-生效中',
                                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
                                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`group_announcement_id`),
                                      KEY `idx_group` (`gid`, `is_pinned`, `status`) COMMENT '群ID+置顶+状态联合索引'
) ENGINE=InnoDB COMMENT='群公告表';