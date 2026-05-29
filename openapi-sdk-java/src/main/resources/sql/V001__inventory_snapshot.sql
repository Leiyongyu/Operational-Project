-- 库存运营数据快照表：定时任务跑完后全量重算写入，API 直接读此表
CREATE TABLE IF NOT EXISTS inventory_snapshot (
    id          INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    data_json   MEDIUMTEXT NOT NULL,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO inventory_snapshot (id, data_json) VALUES (1, '[]')
ON DUPLICATE KEY UPDATE id = id;
