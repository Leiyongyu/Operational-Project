DROP TABLE IF EXISTS ebay_sales;
CREATE TABLE ebay_sales (
    id VARCHAR(32) NOT NULL COMMENT 'UUID',
    platform_order_no VARCHAR(50) NOT NULL COMMENT '平台订单号',
    currency VARCHAR(10) DEFAULT '' COMMENT '币种',
    sku VARCHAR(200) NOT NULL COMMENT '库存SKU',
    quantity INT DEFAULT 0 COMMENT '购买数量',
    payment_time DATETIME COMMENT '付款时间',
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_sku (platform_order_no, sku),
    KEY idx_sku (sku),
    KEY idx_payment_time (payment_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='eBay销量表';
