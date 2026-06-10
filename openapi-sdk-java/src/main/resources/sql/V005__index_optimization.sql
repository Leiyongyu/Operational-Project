-- ====================================================================
-- 性能优化：为关键查询添加复合索引
-- ====================================================================

-- ebay_sales: computeOverview() 按 payment_time 过滤 + currency 筛选
-- 覆盖近365天销量查询（7/30/90天销量 + 月最大销量）
ALTER TABLE ebay_sales ADD INDEX idx_payment_currency (payment_time, currency);
ALTER TABLE ebay_sales ADD INDEX idx_sku_currency_payment (sku, currency, payment_time);

-- warehouse_statement: computeOverview() 按 type=22 + opt_time 过滤
-- 覆盖入库时间查询和采购周期计算
ALTER TABLE warehouse_statement ADD INDEX idx_type_opt_time (type, opt_time);
ALTER TABLE warehouse_statement ADD INDEX idx_sku_wid_type (sku, wid, type);

-- purchase_order: computeOverview() 按 create_time 过滤（近365天）
ALTER TABLE purchase_order ADD INDEX idx_create_time_status (create_time, status_text);

-- purchase_plan: computeOverview() 按 upload_time 过滤（近180天）
ALTER TABLE purchase_plan ADD INDEX idx_status_upload (status_text, upload_time);
ALTER TABLE purchase_plan ADD INDEX idx_upload_time (upload_time);
