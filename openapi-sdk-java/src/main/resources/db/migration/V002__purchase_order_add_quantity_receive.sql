-- purchase_order 表新加待到货量字段
ALTER TABLE purchase_order ADD COLUMN item_quantity_receive INT DEFAULT 0 COMMENT '待到货量' AFTER item_quantity_entry;
