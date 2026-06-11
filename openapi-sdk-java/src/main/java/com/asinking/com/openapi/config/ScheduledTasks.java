package com.asinking.com.openapi.config;

import com.asinking.com.openapi.dto.response.SaleStatSyncResponse;
import com.asinking.com.openapi.dto.response.WarehouseInventoryDetailSyncResponse;
import com.asinking.com.openapi.service.GoodcangProductService;
import com.asinking.com.openapi.service.GoodcangSyncService;
import com.asinking.com.openapi.service.LingxingWarehouseInventoryService;
import com.asinking.com.openapi.service.LingxingPurchaseOrderService;
import com.asinking.com.openapi.service.LingxingPurchasePlanQueryService;
import com.asinking.com.openapi.service.LingxingWarehouseStatementService;
import com.asinking.com.openapi.service.InventoryOverviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时任务调度：每日凌晨依次同步库存/谷仓/流水/采购单/采购计划，完成后刷新运营快照。 */
@Component
public class ScheduledTasks {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledTasks.class);
    private final LingxingWarehouseInventoryService inventoryService;
    private final GoodcangSyncService goodcangSyncService;
    private final LingxingWarehouseStatementService statementService;
    private final LingxingPurchaseOrderService purchaseOrderService;
    private final LingxingPurchasePlanQueryService purchasePlanQueryService;
    private final InventoryOverviewService overviewService;
    private final GoodcangProductService goodcangProductService;

    public ScheduledTasks(LingxingWarehouseInventoryService inventoryService,
                          GoodcangSyncService goodcangSyncService,
                          LingxingWarehouseStatementService statementService,
                          LingxingPurchaseOrderService purchaseOrderService,
                          LingxingPurchasePlanQueryService purchasePlanQueryService,
                          InventoryOverviewService overviewService,
                          GoodcangProductService goodcangProductService) {
        this.inventoryService = inventoryService;
        this.goodcangSyncService = goodcangSyncService;
        this.statementService = statementService;
        this.purchaseOrderService = purchaseOrderService;
        this.purchasePlanQueryService = purchasePlanQueryService;
        this.overviewService = overviewService;
        this.goodcangProductService = goodcangProductService;
    }

    // ===== 每日定时同步（仅同步频繁变动的数据） =====
    // 仓库、eBay商品、谷仓仓库等不常变动，仅在管理员手动"拉取最新数据"时更新

    @Scheduled(cron = "0 5 0 * * ?")
    public void syncInventory() {
        LOG.info("==== 库存同步 开始 ====");
        try { long t = System.currentTimeMillis();
            WarehouseInventoryDetailSyncResponse r = inventoryService.syncAllInventoryDetails(null);
            LOG.info("[库存] 新增{} 耗时{}s", r.getInserted(), (System.currentTimeMillis()-t)/1000);
        } catch (Exception e) { LOG.error("[库存] 失败", e); }
    }

    /** 谷仓入库单增量同步（仅拉取近2天，避免数据量递增） */
    @Scheduled(cron = "0 25 0 * * ?")
    public void syncGoodcangGrn() {
        LOG.info("==== 谷仓入库单同步 开始 ====");
        try { long t = System.currentTimeMillis();
            String to = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String from = java.time.LocalDateTime.now().minusDays(2).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            SaleStatSyncResponse r = goodcangSyncService.syncGrn(from, to);
            LOG.info("[谷仓入库单] 单数:{} 耗时{}s", r.getInserted(), (System.currentTimeMillis()-t)/1000);
        } catch (Exception e) { LOG.error("[谷仓入库单] 失败", e); }
    }

    /** 库存流水增量同步（仅拉取近2天） */
    @Scheduled(cron = "0 30 0 * * ?")
    public void syncWarehouseStatement() {
        LOG.info("==== 仓库库存流水同步 开始 ====");
        try { long t = System.currentTimeMillis();
            LocalDate now = LocalDate.now();
            SaleStatSyncResponse r = statementService.syncStatements(now.minusDays(2).toString(), now.toString());
            LOG.info("[库存流水] 处理:{} 耗时{}s", r.getInserted(), (System.currentTimeMillis()-t)/1000);
        } catch (Exception e) { LOG.error("[库存流水] 失败", e); }
    }

    @Scheduled(cron = "0 35 0 * * ?")
    public void syncPurchaseOrder() {
        LOG.info("==== 采购单同步 开始 ====");
        try { long t = System.currentTimeMillis();
            LocalDate now = LocalDate.now();
            SaleStatSyncResponse r = purchaseOrderService.sync(now.minusDays(1).toString(), now.minusDays(1).toString());
            LOG.info("[采购单] 处理:{} 耗时{}s", r.getInserted(), (System.currentTimeMillis()-t)/1000);
        } catch (Exception e) { LOG.error("[采购单] 失败", e); }
    }

    @Scheduled(cron = "0 40 0 * * ?")
    public void syncPurchasePlan() {
        LOG.info("==== 采购计划同步 开始 ====");
        try { long t = System.currentTimeMillis();
            LocalDate now = LocalDate.now();
            SaleStatSyncResponse r = purchasePlanQueryService.sync(now.minusDays(1).toString(), now.minusDays(1).toString());
            LOG.info("[采购计划] 处理:{} 耗时{}s", r.getInserted(), (System.currentTimeMillis()-t)/1000);
        } catch (Exception e) { LOG.error("[采购计划] 失败", e); }
    }

    /** 所有数据同步完成后（00:45）刷新运营数据快照 */
    @Scheduled(cron = "0 45 0 * * ?")
    public void refreshInventorySnapshot() {
        overviewService.refreshSnapshot();
    }

    /** 每周一凌晨 00:10 同步谷仓商品信息 */
    @Scheduled(cron = "0 10 0 * * 1")
    public void syncGoodcangProducts() {
        LOG.info("==== 谷仓商品同步 开始 ====");
        try {
            long t = System.currentTimeMillis();
            Map<String, Object> r = goodcangProductService.syncFromApi();
            LOG.info("[谷仓商品] 共{}条 新增{} 更新{} 耗时{}s", r.get("total"), r.get("inserted"), r.get("updated"), (System.currentTimeMillis() - t) / 1000);
        } catch (Exception e) {
            LOG.error("[谷仓商品] 失败", e);
        }
    }

}
