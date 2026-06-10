package com.asinking.com.openapi.service;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.dto.response.InventoryOverviewItem;
import com.asinking.com.openapi.dto.response.WarehouseOptionItem;

import java.util.List;

/**
 * 运营组数据看板 — 库存概览服务。
 */
public interface InventoryOverviewService {

    /**
     * 汇总 SKU 维度的库存、销量、库销比等数据，每个 (SKU, 站点) 一行。
     */
    List<InventoryOverviewItem> buildOverview();

    /**
     * 按 SKU / 站点 / 用户品牌过滤库存概览。
     *
     * @param sku       SKU 模糊搜索，null 则不筛选
     * @param warehouse 站点标签精确匹配，null 则不筛选
     * @param userId    当前用户ID，null 则不过滤品牌
     * @param role      当前用户角色，"admin" 看全部
     */
    List<InventoryOverviewItem> filterOverview(String sku, String warehouse, String userId, String role);

    /**
     * 获取库存同步配置的仓库列表，供前端下拉选择。
     */
    List<WarehouseOptionItem> getWarehouseOptions();

    /**
     * 分页查询库存概览。
     */
    PageResult<InventoryOverviewItem> pageOverview(long page, long size, String sku, String warehouse, String userId, String role);

    /**
     * 全量重算运营数据并写入数据库，供定时任务和手动刷新调用。
     */
    void refreshSnapshot();
}
