package com.asinking.com.openapi.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 每日跟价数据响应 DTO — 独立于补货页（InventoryOverviewItem）的数据模型。
 * 部分字段与补货页共享相同的计算逻辑（销量、库存、品牌归属等），
 * 部分字段为每日跟价特有（价格信息、eBay URL 等），按行返回给前端。
 */
@Data
public class DailyPriceTrackingItem {

    // ===== 基础标识（有数据） =====

    /** 站点（国家标签：美国/德国/英国） */
    private String site;
    /** SKU 编码（baseSku） */
    private String sku;
    /** 产品名称 */
    private String productName;
    /** SKU 产品等级（S/A/B/C/D/E），根据销量+毛利率计算 */
    private String skuLevel;

    // ===== 销量数据（有数据，从 ebay_sales 汇总） =====

    /** 近3天销量 */
    private Integer last3DaysSales;
    /** 近7天销量 */
    private Integer last7DaysSales;
    /** 近30天销量 */
    private Integer last30DaysSales;
    /** 近90天销量 */
    private Integer last90DaysSales;
    /** 历史最大月销 */
    private Integer maxMonthlySales;

    // ===== 库存数据（有数据，从 warehouse_inventory_detail 汇总） =====

    /** 海外仓可售库存 */
    private Integer overseasWarehouseStock;
    /** 海外仓库龄（天） */
    private Integer overseasWarehouseAge;
    /** SKU 库销比（海外可售 / 近30天销量） */
    private BigDecimal stockSalesRatio;

    // ===== 采购建议（有数据，从 purchase_order + warehouse_statement + goodcang 计算） =====

    /** 预估补货量 */
    private Integer estimatedReplenish;

    // ===== 价格信息（预留，暂无数据源） =====

    /** 我们的链接当前最低价 */
    private BigDecimal ourLowestPrice;
    /** 跟卖价格 */
    private BigDecimal trackingPrice;
    /** 跟卖价格利润率（%） */
    private BigDecimal trackingProfitMargin;
    /** 底线价 */
    private BigDecimal floorPrice;
    /** 退货率（%） */
    private BigDecimal returnRate;

    // ===== eBay 链接（预留，暂无数据源） =====

    /** eBay 前台首页 URL */
    private String ebayFrontpageUrl;
    /** 前台已售页面 URL */
    private String frontpageSoldUrl;

    // ===== 归属（有数据） =====

    /** 品牌（SKU 前缀） */
    private String brand;
    /** 操作员（品牌负责人） */
    private String operator;

    // ===== 备注（预留） =====

    /** 备注 */
    private String remark;

    /** OE 号（默认来自 ebay_product_listing.oe，用户可手动覆盖并保存） */
    private String oeNumber;
}
