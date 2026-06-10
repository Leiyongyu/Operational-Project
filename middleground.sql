/*
 Navicat Premium Dump SQL

 Source Server         : root
 Source Server Type    : MySQL
 Source Server Version : 90700 (9.7.0)
 Source Host           : localhost:3306
 Source Schema         : middleground

 Target Server Type    : MySQL
 Target Server Version : 90700 (9.7.0)
 File Encoding         : 65001

 Date: 08/06/2026 11:57:04
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for brand_owner
-- ----------------------------
DROP TABLE IF EXISTS `brand_owner`;
CREATE TABLE `brand_owner`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `brand_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '品牌代码',
  `owner_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '负责人姓名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_brand_code`(`brand_code` ASC) USING BTREE,
  INDEX `idx_owner_name`(`owner_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 34 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '品牌负责人表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for ebay_link_template
-- ----------------------------
DROP TABLE IF EXISTS `ebay_link_template`;
CREATE TABLE `ebay_link_template`  (
  `site` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '站点',
  `presale_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '售前链接模板',
  `sold_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '售后链接模板',
  `profit_rate` int NULL DEFAULT NULL COMMENT '目标利润率(%)',
  `exchange_rate` decimal(10, 2) NULL DEFAULT NULL COMMENT '实时汇率',
  PRIMARY KEY (`site`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ebay_product_dedup
-- ----------------------------
DROP TABLE IF EXISTS `ebay_product_dedup`;
CREATE TABLE `ebay_product_dedup`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `site` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '站点',
  `sku` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'SKU (baseSku)',
  `oe_number` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'OE号',
  `product_name` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '产品名称',
  `tracking_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '跟卖价格',
  `tracking_profit_margin` decimal(10, 6) NULL DEFAULT NULL COMMENT '跟卖利润率',
  `floor_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '底线价',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注',
  `profit_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '近30天利润率',
  `return_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '退货率',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_site_sku`(`site` ASC, `sku` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2417 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ebay商品去重表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ebay_product_listing
-- ----------------------------
DROP TABLE IF EXISTS `ebay_product_listing`;
CREATE TABLE `ebay_product_listing`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID，使用UUID存储（32位，不含连字符）',
  `platform` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'eBay' COMMENT '平台名称，默认eBay，可根据需要修改',
  `item_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'eBay商品ID，唯一标识',
  `item_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品链接地址',
  `picture_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '商品图片URL',
  `msku` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '制造商SKU编码',
  `sku` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '基础SKU(前两段)',
  `local_sku` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '本地SKU编码',
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品标题',
  `local_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '商品本地名称（中文名称）',
  `attribute` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '商品属性，JSON格式存储',
  `listing_type` tinyint NOT NULL COMMENT '刊登类型：2-固价',
  `listing_type_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '刊登类型名称',
  `listing_status` tinyint NOT NULL COMMENT '刊登状态：2-已下架',
  `listing_status_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '刊登状态名称',
  `price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '商品价格',
  `start_price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '起始价格',
  `accept_price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '接受价格',
  `quantity` int NOT NULL DEFAULT 0 COMMENT '商品库存数量',
  `auto_restock` tinyint NULL DEFAULT NULL COMMENT '自动补货设置，0-关闭，1-开启',
  `product_auto_restock_response` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '自动补货响应配置，JSON格式',
  `location` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '商品所在地',
  `dispatch_time_max` tinyint NULL DEFAULT NULL COMMENT '最长发货时间（天）',
  `listing_start_time` datetime NOT NULL COMMENT '刊登开始时间',
  `listing_end_time` datetime NOT NULL COMMENT '刊登结束时间',
  `store_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '店铺ID，关联店铺表',
  `store_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '店铺名称',
  `site_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '站点代码',
  `site_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '站点名称',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_item_id`(`item_id` ASC) USING BTREE,
  INDEX `idx_store_id`(`store_id` ASC) USING BTREE,
  INDEX `idx_listing_status`(`listing_status` ASC) USING BTREE,
  INDEX `idx_listing_start_time`(`listing_start_time` ASC) USING BTREE,
  INDEX `idx_listing_end_time`(`listing_end_time` ASC) USING BTREE,
  INDEX `idx_platform`(`platform` ASC) USING BTREE,
  INDEX `idx_quantity`(`quantity` ASC) USING BTREE,
  INDEX `idx_msku_local_sku`(`msku` ASC, `local_sku` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'eBay商品刊登信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for ebay_sales
-- ----------------------------
DROP TABLE IF EXISTS `ebay_sales`;
CREATE TABLE `ebay_sales`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'UUID',
  `platform_order_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '平台订单号',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '币种',
  `sku` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '库存SKU',
  `quantity` int NULL DEFAULT 0 COMMENT '购买数量',
  `payment_time` datetime NULL DEFAULT NULL COMMENT '付款时间',
  `upload_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_sku`(`platform_order_no` ASC, `sku` ASC) USING BTREE,
  INDEX `idx_sku`(`sku` ASC) USING BTREE,
  INDEX `idx_payment_time`(`payment_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'eBay销量表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for ebay_shop_list
-- ----------------------------
DROP TABLE IF EXISTS `ebay_shop_list`;
CREATE TABLE `ebay_shop_list`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID，使用UUID存储（格式：32位字符串，不含连字符）',
  `store_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '店铺ID，eBay店铺唯一标识',
  `sid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT 'SID标识，默认为空字符串',
  `store_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '店铺名称',
  `platform_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '平台代码，10003代表eBay平台',
  `platform_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '平台名称',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '店铺使用的货币单位，如HKD港币',
  `is_sync` tinyint NOT NULL DEFAULT 0 COMMENT '是否同步数据，1表示同步，0表示未同步',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '店铺状态，1表示启用，0表示禁用',
  `country_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '国家/地区代码，如HK香港',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_store_id`(`store_id` ASC) USING BTREE,
  INDEX `idx_platform_code`(`platform_code` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_country_code`(`country_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'eBay店铺列表信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for goodcang_grn_detail
-- ----------------------------
DROP TABLE IF EXISTS `goodcang_grn_detail`;
CREATE TABLE `goodcang_grn_detail`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'UUID',
  `receiving_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '入库单号',
  `product_sku` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '商品SKU',
  `box_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '箱号',
  `transit_pre_count` int NULL DEFAULT 0 COMMENT '中转预报数量',
  `transit_receiving_count` int NULL DEFAULT 0 COMMENT '中转收货数量',
  `reference_box_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '第三方箱唛号',
  `upload_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_receiving_code`(`receiving_code` ASC) USING BTREE,
  INDEX `idx_product_sku`(`product_sku` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '谷仓入库单详情(中转明细)' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for goodcang_grn_list
-- ----------------------------
DROP TABLE IF EXISTS `goodcang_grn_list`;
CREATE TABLE `goodcang_grn_list`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'UUID',
  `receiving_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '入库单号',
  `warehouse_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '海外仓仓库编码',
  `transit_warehouse_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '中转仓仓库编码',
  `reference_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '客户参考号',
  `receiving_status` int NULL DEFAULT 0 COMMENT '入库单状态',
  `transit_type` int NULL DEFAULT 0 COMMENT '入库单类型',
  `create_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_at` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `upload_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_receiving_code`(`receiving_code` ASC) USING BTREE,
  INDEX `idx_warehouse`(`warehouse_code` ASC) USING BTREE,
  INDEX `idx_create_at`(`create_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '谷仓入库单列表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for goodcang_product_info
-- ----------------------------
DROP TABLE IF EXISTS `goodcang_product_info`;
CREATE TABLE `goodcang_product_info`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sku_middle` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '中间码',
  `product_name_cn` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '中文名称',
  `real_weight` decimal(10, 3) NULL DEFAULT NULL COMMENT '实收重量(KG)',
  `real_length` decimal(10, 2) NULL DEFAULT NULL COMMENT '实收长(CM)',
  `real_width` decimal(10, 2) NULL DEFAULT NULL COMMENT '实收宽(CM)',
  `real_height` decimal(10, 2) NULL DEFAULT NULL COMMENT '实收高(CM)',
  `volume` decimal(10, 2) NULL DEFAULT NULL COMMENT '体积(长*宽*高/6000)',
  `price` decimal(10, 2) NULL DEFAULT NULL COMMENT '单价',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `middle_code`(`sku_middle` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2236 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for goodcang_warehouse
-- ----------------------------
DROP TABLE IF EXISTS `goodcang_warehouse`;
CREATE TABLE `goodcang_warehouse`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'UUID',
  `warehouse_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '区域仓代码',
  `wid` int NULL DEFAULT 0 COMMENT '关联warehouse.wid',
  `warehouse_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '仓库名称',
  `country_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '国家代码',
  `wp_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '物理仓编码',
  `wp_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '物理仓名称',
  `state` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '州/省份',
  `city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '城市',
  `postcode` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '邮编',
  `contacter` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '联系人',
  `phone` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '电话',
  `street_address1` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '地址1',
  `street_address2` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '地址2',
  `street_number` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '门牌号',
  `upload_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_wp_code`(`wp_code` ASC) USING BTREE,
  INDEX `idx_warehouse_code`(`warehouse_code` ASC) USING BTREE,
  INDEX `idx_country_code`(`country_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '谷仓仓库信息(物理仓级别)' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for inventory_snapshot
-- ----------------------------
DROP TABLE IF EXISTS `inventory_snapshot`;
CREATE TABLE `inventory_snapshot`  (
  `id` int NOT NULL DEFAULT 1,
  `data_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据快照表，提高前端数据访问数据' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for lowest_price_record
-- ----------------------------
DROP TABLE IF EXISTS `lowest_price_record`;
CREATE TABLE `lowest_price_record`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `site` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '站点',
  `sku` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'SKU',
  `item_number` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'eBay Item Number',
  `lowest_price` decimal(10, 2) NOT NULL COMMENT '最低价格',
  `upload_time` datetime NULL DEFAULT NULL COMMENT '最近上传时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_site_sku`(`site` ASC, `sku` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '每日跟价最低价格记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for operation_log
-- ----------------------------
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operation_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作类型: 拉取/上传/同步',
  `target` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '目标表或接口',
  `status` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '成功/失败',
  `total_count` int NULL DEFAULT NULL COMMENT '总条数',
  `success_count` int NULL DEFAULT NULL COMMENT '成功条数',
  `fail_count` int NULL DEFAULT NULL COMMENT '失败条数',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '拉取数据日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for purchase_order
-- ----------------------------
DROP TABLE IF EXISTS `purchase_order`;
CREATE TABLE `purchase_order`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'UUID',
  `order_sn` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '采购单号',
  `custom_order_sn` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '自定义单号',
  `supplier_id` int NULL DEFAULT 0 COMMENT '供应商id',
  `supplier_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '供应商',
  `opt_uid` int NULL DEFAULT 0 COMMENT '采购员id',
  `opt_realname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '操作人姓名',
  `auditor_realname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '审核人姓名',
  `last_realname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '最后操作人姓名',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `order_time` datetime NULL DEFAULT NULL COMMENT '下单时间',
  `update_time` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '采购单更新时间',
  `status` int NULL DEFAULT 0 COMMENT '采购单状态',
  `status_text` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '状态说明',
  `wid` int NULL DEFAULT 0 COMMENT '仓库id',
  `ware_house_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '仓库名',
  `item_sku` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '子项SKU',
  `item_product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '子项品名',
  `item_product_id` int NULL DEFAULT 0 COMMENT '子项产品id',
  `item_quantity_real` int NULL DEFAULT 0 COMMENT '子项实际采购量',
  `item_quantity_entry` int NULL DEFAULT 0 COMMENT '子项到货入库量',
  `item_quantity_receive` int NULL DEFAULT 0 COMMENT '待到货量',
  `item_price` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '子项含税单价',
  `item_amount` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '子项价税合计',
  `upload_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_create`(`order_sn` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_item_sku`(`item_sku` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采购单(全字段)' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for purchase_plan
-- ----------------------------
DROP TABLE IF EXISTS `purchase_plan`;
CREATE TABLE `purchase_plan`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'UUID',
  `plan_sn` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '采购计划编号',
  `ppg_sn` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '采购计划批次号',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '品名',
  `sku` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'SKU',
  `fnsku` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'FNSKU',
  `pic_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '产品图片',
  `supplier_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '供应商id',
  `supplier_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '供应商名称',
  `status_text` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '状态说明',
  `status` int NULL DEFAULT 0 COMMENT '状态值: 2待采购 -2已完成 121待审批 122已驳回 -3/124已作废',
  `sid` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '店铺id',
  `seller_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '店铺名称',
  `marketplace` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '国家',
  `expect_arrive_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '期望到货时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '产品备注',
  `quantity_plan` int NULL DEFAULT 0 COMMENT '计划采购量',
  `product_id` int NULL DEFAULT 0 COMMENT '商品id',
  `cg_uid` int NULL DEFAULT 0 COMMENT '采购员id',
  `cg_opt_username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '采购员名称',
  `cg_box_pcs` int NULL DEFAULT 0 COMMENT '单箱数量',
  `is_combo` int NULL DEFAULT 0 COMMENT '组合商品: 0否 1是',
  `is_aux` int NULL DEFAULT 0 COMMENT '辅料: 0否 1是',
  `is_related_process_plan` int NULL DEFAULT 0 COMMENT '关联加工计划: 0否 1是',
  `spu` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'SPU',
  `spu_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '款名',
  `creator_uid` int NULL DEFAULT 0 COMMENT '创建人id',
  `creator_real_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人名称',
  `wid` int NULL DEFAULT 0 COMMENT '仓库id',
  `warehouse_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '仓库名称',
  `purchaser_id` int NULL DEFAULT 0 COMMENT '采购方id',
  `purchaser_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '采购方名称',
  `create_time` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建时间',
  `plan_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '计划备注',
  `attribute_json` json NULL COMMENT '属性',
  `file_json` json NULL COMMENT '附件',
  `msku_json` json NULL COMMENT 'MSKU',
  `perm_uid_json` json NULL COMMENT '单据负责人uid',
  `perm_username_json` json NULL COMMENT '单据负责人名称',
  `upload_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_plan_sku`(`plan_sn` ASC, `sku` ASC) USING BTREE,
  INDEX `idx_ppg_sn`(`ppg_sn` ASC) USING BTREE,
  INDEX `idx_sku`(`sku` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采购计划(全字段)' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for purchase_plan_submit
-- ----------------------------
DROP TABLE IF EXISTS `purchase_plan_submit`;
CREATE TABLE `purchase_plan_submit`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '主键UUID',
  `sku` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'SKU',
  `wid` int NOT NULL COMMENT '仓库ID',
  `warehouse_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `quantity_plan` int NOT NULL COMMENT '计划采购量',
  `quantity_replenish` int NULL DEFAULT 0 COMMENT '预估补货量',
  `quantity_purchase` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '采购数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注（等级|销量|利润）',
  `expect_arrive_time` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '期望到货时间',
  `plan_sn` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '领星返回的采购计划单号',
  `ppg_sn` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '领星返回的批次号',
  `status_text` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '已提交' COMMENT '状态',
  `creator_owner_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人（负责人名称）',
  `creator_account` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人账号',
  `creator_role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'user' COMMENT '创建人角色',
  `approver` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审批人账号',
  `approve_time` datetime NULL DEFAULT NULL COMMENT '审批时间',
  `submit_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_owner`(`creator_owner_name` ASC) USING BTREE,
  INDEX `idx_account`(`creator_account` ASC) USING BTREE,
  INDEX `idx_submit_time`(`submit_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采购计划提交记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for team
-- ----------------------------
DROP TABLE IF EXISTS `team`;
CREATE TABLE `team`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `leader` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '组长（user表的owner_name）',
  `member` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '组员（user表的owner_name）',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_leader_member`(`leader` ASC, `member` ASC) USING BTREE,
  INDEX `idx_leader`(`leader` ASC) USING BTREE,
  INDEX `idx_member`(`member` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '团队关系表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()) COMMENT '主键ID，使用UUID生成',
  `account` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号，唯一',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（加密后存储，如BCrypt、MD5等）',
  `role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色（如：admin、manager、viewer等）',
  `owner_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '负责人姓名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `account`(`account` ASC) USING BTREE,
  INDEX `idx_account`(`account` ASC) USING BTREE,
  INDEX `idx_role`(`role` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for warehouse
-- ----------------------------
DROP TABLE IF EXISTS `warehouse`;
CREATE TABLE `warehouse`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'UUID主键',
  `wid` int NOT NULL COMMENT '领星仓库ID',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '仓库名称',
  `type` int NULL DEFAULT 0 COMMENT '仓库类型',
  `sub_type` int NULL DEFAULT 0 COMMENT '仓库子类型',
  `is_delete` int NULL DEFAULT 0 COMMENT '删除标记',
  `country_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '国家代码',
  `wp_id` int NULL DEFAULT 0 COMMENT '仓储服务商ID',
  `wp_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '仓储服务商名称',
  `t_warehouse_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '第三方仓库名称',
  `t_warehouse_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '第三方仓库编码',
  `t_country_area_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '第三方国家地区名称',
  `t_status` int NULL DEFAULT 0 COMMENT '第三方状态',
  `raw_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '原始JSON数据',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_wid`(`wid` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '海外仓仓库表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for warehouse_inventory_detail
-- ----------------------------
DROP TABLE IF EXISTS `warehouse_inventory_detail`;
CREATE TABLE `warehouse_inventory_detail`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'UUID主键',
  `wid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '仓库id',
  `product_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '本地产品id',
  `sku` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'SKU',
  `seller_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '店铺id',
  `fnsku` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'FNSKU',
  `product_total` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '实际库存总量(可用量+次品量+待检待上架量+锁定量)',
  `product_valid_num` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '可用量',
  `product_bad_num` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '次品量',
  `product_qc_num` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '待检待上架量',
  `product_lock_num` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '锁定量',
  `good_lock_num` int NULL DEFAULT 0 COMMENT '良品锁定数',
  `bad_lock_num` int NULL DEFAULT 0 COMMENT '不良品锁定数',
  `stock_cost_total` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '库存成本',
  `quantity_receive` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '待到货量',
  `stock_cost` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '单位库存成本',
  `product_onway` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '调拨在途',
  `transit_head_cost` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '调拨在途头程成本',
  `average_age` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '平均库龄',
  `expect_valid_num` int NULL DEFAULT 0 COMMENT '海外仓预期有效数',
  `expect_pending_num` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '海外仓预期待处理数',
  `available_inventory_box_qty` int NULL DEFAULT 0 COMMENT '海外仓可用箱库存',
  `purchase_price` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '采购单价',
  `price` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '单位费用',
  `head_stock_price` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '单位头程',
  `stock_price` decimal(16, 4) NULL DEFAULT 0.0000 COMMENT '单位库存成本',
  `third_inventory_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '海外仓第三方库存信息(JSON)',
  `stock_age_list_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '库龄信息(JSON)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_wid_product`(`wid` ASC, `product_id` ASC) USING BTREE,
  INDEX `idx_sku`(`sku` ASC) USING BTREE,
  INDEX `idx_wid`(`wid` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '仓库库存明细表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for warehouse_statement
-- ----------------------------
DROP TABLE IF EXISTS `warehouse_statement`;
CREATE TABLE `warehouse_statement`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'UUID',
  `statement_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '流水ID',
  `wid` int NULL DEFAULT 0 COMMENT '仓库id',
  `ware_house_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '仓库名称',
  `order_sn` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '操作单据号',
  `ref_order_sn` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '关联单据号',
  `sku` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'SKU',
  `seller_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '店铺id',
  `fnsku` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT 'FNSKU',
  `opt_time` datetime NULL DEFAULT NULL COMMENT '操作时间',
  `type` int NULL DEFAULT 0 COMMENT '流水类型',
  `type_text` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '流水类型文本',
  `sub_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '子类型',
  `sub_type_text` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '子类型文本',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '品名',
  `product_good_num` int NULL DEFAULT 0 COMMENT '可用量',
  `product_bad_num` int NULL DEFAULT 0 COMMENT '次品量',
  `upload_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_stmt`(`statement_id` ASC) USING BTREE,
  INDEX `idx_sku`(`sku` ASC) USING BTREE,
  INDEX `idx_wid`(`wid` ASC) USING BTREE,
  INDEX `idx_opt_time`(`opt_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '仓库库存流水' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
