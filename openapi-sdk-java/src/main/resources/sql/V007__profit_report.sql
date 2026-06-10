-- ====================================================================
-- 利润报表表：存储 Excel 上传的利润数据
-- 按 (msku, ship_time, store_name, country_code) 联合键 upsert
-- ====================================================================
CREATE TABLE IF NOT EXISTS profit_report (
    id              VARCHAR(32)  NOT NULL COMMENT 'UUID主键',
    file_name       VARCHAR(200) DEFAULT '' COMMENT '上传文件名',
    msku            VARCHAR(200) NOT NULL COMMENT '商品MSKU',
    platform        VARCHAR(50)  DEFAULT '' COMMENT '平台名称',
    store_name      VARCHAR(200) DEFAULT '' COMMENT '店铺名称',
    country_code    VARCHAR(10)  DEFAULT '' COMMENT '国家代码',
    currency_code   VARCHAR(10)  DEFAULT '' COMMENT '币种',
    volume          INT          DEFAULT 0  COMMENT '销量',
    sales_amount    DECIMAL(16,4) DEFAULT 0.0000 COMMENT '销售额',
    gross_profit    DECIMAL(16,4) DEFAULT 0.0000 COMMENT '毛利',
    gross_margin    DECIMAL(10,4) DEFAULT NULL COMMENT '毛利率',
    purchase_cost   DECIMAL(16,4) DEFAULT 0.0000 COMMENT '采购成本',
    logistics_cost  DECIMAL(16,4) DEFAULT 0.0000 COMMENT '物流成本',
    ship_time       VARCHAR(50)  DEFAULT '' COMMENT '发货时间',
    upload_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (id) USING BTREE,
    UNIQUE INDEX uk_msku_ship_store_country (msku, ship_time, store_name, country_code),
    INDEX idx_msku (msku),
    INDEX idx_store_name (store_name),
    INDEX idx_ship_time (ship_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='利润报表表';
