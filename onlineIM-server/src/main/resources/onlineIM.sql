CREATE TABLE `users` (
                         `id` VARCHAR(143) NOT NULL COMMENT '用户ID，格式为：usr_+UUID',
                         `username` VARCHAR(32) NOT NULL COMMENT '用户名',
                         `password` VARCHAR(128) NOT NULL COMMENT '密码哈希值',
                         `nickname` VARCHAR(40) NOT NULL COMMENT '昵称',
                         `avatar` VARCHAR(256) DEFAULT NULL COMMENT '头像URL',
                         `gender` TINYINT UNSIGNED DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
                         `signature` VARCHAR(256) DEFAULT NULL COMMENT '个性签名',
                         `region` VARCHAR(64) DEFAULT NULL COMMENT '地区',
                         `email` VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
                         `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
                         `status` TINYINT UNSIGNED DEFAULT 1 COMMENT '账号状态：0-禁用，1-正常',
                         `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
                         `last_login_ip` VARCHAR(64) DEFAULT NULL COMMENT '最后登录IP',
                         `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_username` (`username`),
                         KEY `idx_nickname` (`nickname`),
                         KEY `idx_phone` (`phone`),
                         KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

CREATE TABLE `user_friends` (
                                `id` VARCHAR(143) NOT NULL COMMENT '关系ID，格式为：rel_+UUID',
                                `user_id` VARCHAR(143) NOT NULL COMMENT '用户ID，格式为：usr_+UUID',
                                `friend_id` VARCHAR(143) NOT NULL COMMENT '好友ID，格式为：usr_+UUID',
                                `remark` VARCHAR(32) DEFAULT NULL COMMENT '好友备注',
                                `group_id` VARCHAR(143) DEFAULT NULL COMMENT '好友分组ID，格式为：fgrp_+UUID',
                                `status` TINYINT UNSIGNED DEFAULT 1 COMMENT '好友状态：0-已拉黑，1-正常',
                                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
                                KEY `idx_friend_id` (`friend_id`),
                                KEY `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系表';

CREATE TABLE `friend_requests` (
                                   `id` VARCHAR(143) NOT NULL COMMENT '请求ID，格式为：req_+UUID',
                                   `from_user_id` VARCHAR(143) NOT NULL COMMENT '发送者ID，格式为：usr_+UUID',
                                   `to_user_id` VARCHAR(143) NOT NULL COMMENT '接收者ID，格式为：usr_+UUID',
                                   `message` VARCHAR(256) DEFAULT NULL COMMENT '验证消息',
                                   `status` TINYINT UNSIGNED DEFAULT 0 COMMENT '状态：0-待处理，1-已同意，2-已拒绝',
                                   `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_friend_request` (`from_user_id`, `to_user_id`),
                                   KEY `idx_from_user` (`from_user_id`, `status`),
                                   KEY `idx_to_user` (`to_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友请求表';

CREATE TABLE `friend_groups` (
                                 `id` VARCHAR(143) NOT NULL COMMENT '分组ID，格式为：fgrp_+UUID',
                                 `user_id` VARCHAR(143) NOT NULL COMMENT '用户ID，格式为：usr_+UUID',
                                 `name` VARCHAR(32) NOT NULL COMMENT '分组名称',
                                 `sort` INT UNSIGNED DEFAULT 0 COMMENT '排序权重',
                                 `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友分组表';

CREATE TABLE `groups` (
                          `id` VARCHAR(143) NOT NULL COMMENT '群组ID，格式为：grp_+UUID',
                          `name` VARCHAR(64) NOT NULL COMMENT '群组名称',
                          `avatar` VARCHAR(256) DEFAULT NULL COMMENT '群头像URL',
                          `description` VARCHAR(512) DEFAULT NULL COMMENT '群组描述',
                          `owner_id` VARCHAR(143) NOT NULL COMMENT '群主ID，格式为：usr_+UUID',
                          `max_members` INT UNSIGNED DEFAULT 500 COMMENT '最大成员数',
                          `current_members` INT UNSIGNED DEFAULT 1 COMMENT '当前成员数',
                          `join_type` TINYINT UNSIGNED DEFAULT 0 COMMENT '加群方式：0-需要验证，1-无需验证，2-禁止加入',
                          `status` TINYINT UNSIGNED DEFAULT 1 COMMENT '群组状态：0-已解散，1-正常',
                          `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          KEY `idx_owner_id` (`owner_id`),
                          KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群组信息表';

CREATE TABLE `group_settings` (
                                  `id` VARCHAR(143) NOT NULL COMMENT '设置ID，格式为：set_+UUID',
                                  `group_id` VARCHAR(143) NOT NULL COMMENT '群组ID，格式为：grp_+UUID',
                                  `allow_member_invite` TINYINT UNSIGNED DEFAULT 1 COMMENT '是否允许成员邀请：0-禁止，1-允许',
                                  `allow_member_modify_name` TINYINT UNSIGNED DEFAULT 1 COMMENT '是否允许成员修改群名片：0-禁止，1-允许',
                                  `allow_member_upload_file` TINYINT UNSIGNED DEFAULT 1 COMMENT '是否允许成员上传文件：0-禁止，1-允许',
                                  `allow_member_at_all` TINYINT UNSIGNED DEFAULT 0 COMMENT '是否允许成员@全体成员：0-禁止，1-允许',
                                  `allow_view_history_message` TINYINT UNSIGNED DEFAULT 1 COMMENT '是否允许查看历史消息：0-禁止，1-允许',
                                  `mute_type` TINYINT UNSIGNED DEFAULT 0 COMMENT '禁言类型：0-不禁言，1-全员禁言（除群主和管理员）',
                                  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群组设置表';

CREATE TABLE `group_announcements` (
                                       `id` VARCHAR(143) NOT NULL COMMENT '公告ID，格式为：ann_+UUID',
                                       `group_id` VARCHAR(143) NOT NULL COMMENT '群组ID，格式为：grp_+UUID',
                                       `publisher_id` VARCHAR(143) NOT NULL COMMENT '发布者ID，格式为：usr_+UUID',
                                       `title` VARCHAR(128) NOT NULL COMMENT '公告标题',
                                       `content` TEXT NOT NULL COMMENT '公告内容',
                                       `status` TINYINT UNSIGNED DEFAULT 1 COMMENT '公告状态：0-已删除，1-正常',
                                       `pin` TINYINT UNSIGNED DEFAULT 0 COMMENT '是否置顶：0-不置顶，1-置顶',
                                       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       PRIMARY KEY (`id`),
                                       KEY `idx_group_id` (`group_id`, `status`, `pin`),
                                       KEY `idx_publisher_id` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群组公告表';

CREATE TABLE `group_members` (
                                 `id` VARCHAR(143) NOT NULL COMMENT '记录ID，格式为：mem_+UUID',
                                 `group_id` VARCHAR(143) NOT NULL COMMENT '群组ID，格式为：grp_+UUID',
                                 `user_id` VARCHAR(143) NOT NULL COMMENT '用户ID，格式为：usr_+UUID',
                                 `nickname` VARCHAR(32) DEFAULT NULL COMMENT '群内昵称',
                                 `role` TINYINT UNSIGNED DEFAULT 0 COMMENT '角色：0-普通成员，1-管理员，2-群主',
                                 `mute_end_time` DATETIME DEFAULT NULL COMMENT '禁言结束时间',
                                 `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
                                 `status` TINYINT UNSIGNED DEFAULT 1 COMMENT '成员状态：0-已退出，1-正常',
                                 `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
                                 KEY `idx_user_id` (`user_id`),
                                 KEY `idx_role` (`group_id`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群组成员表';

CREATE TABLE `group_join_requests` (
                                       `id` VARCHAR(143) NOT NULL COMMENT '请求ID，格式为：jrq_+UUID',
                                       `group_id` VARCHAR(143) NOT NULL COMMENT '群组ID，格式为：grp_+UUID',
                                       `user_id` VARCHAR(143) NOT NULL COMMENT '申请人ID，格式为：usr_+UUID',
                                       `inviter_id` VARCHAR(143) DEFAULT NULL COMMENT '邀请人ID，格式为：usr_+UUID',
                                       `message` VARCHAR(256) DEFAULT NULL COMMENT '验证消息',
                                       `status` TINYINT UNSIGNED DEFAULT 0 COMMENT '状态：0-待处理，1-已同意，2-已拒绝',
                                       `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       PRIMARY KEY (`id`),
                                       KEY `idx_group_id` (`group_id`, `status`),
                                       KEY `idx_user_id` (`user_id`),
                                       KEY `idx_inviter_id` (`inviter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群组加入请求表';