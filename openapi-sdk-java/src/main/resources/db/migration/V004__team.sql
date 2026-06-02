-- 团队管理表：组长和组员的对应关系
CREATE TABLE IF NOT EXISTS team (
    id          INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    leader      VARCHAR(100) NOT NULL COMMENT '组长（user表的owner_name）',
    member      VARCHAR(100) NOT NULL COMMENT '组员（user表的owner_name）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_leader_member (leader, member),
    INDEX idx_leader (leader),
    INDEX idx_member (member)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队关系表';
