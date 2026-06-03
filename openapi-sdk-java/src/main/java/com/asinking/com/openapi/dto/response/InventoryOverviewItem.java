package com.asinking.com.openapi.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 运营组数据看板 — 库存概览汇总，每个 SKU 一行。
 */
@Data
public class InventoryOverviewItem {

    /** 站点（仓库名称，多个用逗号分隔） */
    private String warehouseNames;
    /** SKU */
    private String sku;
    /** 产品名称 */
    private String productName;
    /** 近30天毛利率（百分比，如 7.95 表示 7.95%） */
    private BigDecimal last30DaysProfit;
    /** 退货率（预留，暂无计算逻辑） */
    private BigDecimal returnRate;
    /** 海外在途（调拨在途） */
    private int overseasOnway;
    /** 海外可售（可用量） */
    private int overseasSellable;
    /** 海外总库存 = 海外可售 + 海外在途 */
    private int overseasTotal;
    /** 采购待交付 */
    private int purchasePendingDelivery;
    /** 成都在途（待到货量） */
    private int localOnway;
    /** 成都可售（可用量） */
    private int localSellable;
    /** 采购计划（暂留空） */
    private String purchasePlan;
    /** 待出库（可用锁定量） */
    private int lockNum;
    /** 整个周期总库存 = 海外在途 + 海外可售 + 成都在途 + 成都可售 */
    private int totalInventory;
    /** 近7天销量 */
    private int last7DaysSales;
    /** 近30天销量 */
    private int last30DaysSales;
    /** 近3月销量 */
    private int last90DaysSales;
    /** 历史最大月销（暂留空） */
    private Integer maxMonthlySales;
    /** 海外在库库销比 = 海外可售 / 近30天销量 */
    private BigDecimal overseasInStockRatio;
    /** 海外总库销比 = 海外总库存 / 近30天销量 */
    private BigDecimal overseasTotalRatio;
    /** 总库存库销比 = 整个周期总库存 / 近30天销量 */
    private BigDecimal totalInventoryRatio;
    /** 最近成都仓出库创建时间（暂留空） */
    private String lastLocalOutboundTime;
    /** 出库天数（暂留空） */
    private Integer outboundDays;
    /** 采购周期（暂留空） */
    private Integer purchaseCycle;
    /** 采购数量 = 近3月均销量 * (采购周期 + 出库天数) */
    private BigDecimal purchaseQuantity;
    /** 最大月销预估补货量（暂留空） */
    private Integer maxMonthlyReplenish;
    /** 负责人（根据 SKU 前缀匹配 brand_owner 表） */
    private String owner;
    /** SKU产品等级（S/A/B/C/D/E），根据近30天销量和毛利率计算，快照刷新时更新 */
    private String skuLevel;
}
