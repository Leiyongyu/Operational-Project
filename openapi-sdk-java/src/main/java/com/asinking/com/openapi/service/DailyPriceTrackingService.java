package com.asinking.com.openapi.service;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.dto.response.DailyPriceTrackingItem;

/**
 * 每日跟价服务：独立于补货页（InventoryOverviewService）的数据查询与计算。
 * 直接从各数据源查询，使用共享工具方法，返回专用 DTO。
 */
public interface DailyPriceTrackingService {

    /**
     * 分页查询每日跟价数据，支持站点、SKU、品牌（负责人）、操作员筛选。
     * @return 分页结果，records 为 {@link DailyPriceTrackingItem} 列表
     */
    PageResult<DailyPriceTrackingItem> page(long page, long size,
                                            String site, String sku, String brand, String operator);

    /** 重算并写入数据库 */
    void refreshTable();
}
