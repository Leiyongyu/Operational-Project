package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.request.WarehouseSyncRequest;
import com.asinking.com.openapi.dto.response.WarehouseInventoryDetailSyncResponse;
import com.asinking.com.openapi.dto.response.WarehouseSyncResponse;
import com.asinking.com.openapi.service.LingxingWarehouseInventoryService;
import com.asinking.com.openapi.service.LingxingWarehouseService;
import com.asinking.com.openapi.service.LingxingPurchaseOrderService;
import com.asinking.com.openapi.service.LingxingWarehouseStatementService;
import com.asinking.com.openapi.service.LingxingPurchasePlanQueryService;
import com.asinking.com.openapi.service.LingxingEbayService;
import com.asinking.com.openapi.service.GoodcangSyncService;
import com.asinking.com.openapi.service.InventoryOverviewService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一键同步接口：手动触发从领星拉取仓库和库存数据。
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final LingxingWarehouseService warehouseService;
    private final LingxingWarehouseInventoryService inventoryService;
    private final LingxingWarehouseStatementService statementService;
    private final LingxingPurchaseOrderService purchaseOrderService;
    private final LingxingPurchasePlanQueryService purchasePlanQueryService;
    private final LingxingEbayService ebayService;
    private final GoodcangSyncService goodcangSyncService;
    private final InventoryOverviewService overviewService;

    /** 构造器注入所有同步相关的服务。 */
    public SyncController(LingxingWarehouseService warehouseService,
                          LingxingWarehouseInventoryService inventoryService,
                          LingxingWarehouseStatementService statementService,
                          LingxingPurchaseOrderService purchaseOrderService,
                          LingxingPurchasePlanQueryService purchasePlanQueryService,
                          LingxingEbayService ebayService,
                          GoodcangSyncService goodcangSyncService,
                          InventoryOverviewService overviewService) {
        this.warehouseService = warehouseService;
        this.inventoryService = inventoryService;
        this.statementService = statementService;
        this.purchaseOrderService = purchaseOrderService;
        this.purchasePlanQueryService = purchasePlanQueryService;
        this.ebayService = ebayService;
        this.goodcangSyncService = goodcangSyncService;
        this.overviewService = overviewService;
    }

    /** 一键全量同步：仓库、库存、eBay商品、谷仓仓库/入库单、采购单、采购计划、库存流水，完成后刷新快照。 */
    @PostMapping("/all")
    public Result<Map<String, Object>> syncAll() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        long totalStart = System.currentTimeMillis();
        LocalDate now = LocalDate.now();

        // 仓库
        try { long t = System.currentTimeMillis();
            WarehouseSyncResponse r = warehouseService.syncOverseaWarehouses(new WarehouseSyncRequest());
            result.put("warehouse", map("inserted", r.getInserted(), "updated", r.getUpdated(), "elapsed", ms(t)));
        } catch (Exception e) { result.put("warehouse", error(e)); }

        // eBay商品
        try { long t = System.currentTimeMillis();
            var r = ebayService.syncAllEbayItems(null);
            result.put("ebayItems", map("total", r.getRemoteTotal(), "elapsed", ms(t)));
        } catch (Exception e) { result.put("ebayItems", error(e)); }

        // 库存
        try { long t = System.currentTimeMillis();
            WarehouseInventoryDetailSyncResponse r = inventoryService.syncAllInventoryDetails(null);
            result.put("inventory", map("inserted", r.getInserted(), "elapsed", ms(t)));
        } catch (Exception e) { result.put("inventory", error(e)); }

        // 谷仓仓库
        try { long t = System.currentTimeMillis();
            var r = goodcangSyncService.syncWarehouses();
            result.put("goodcangWarehouses", map("inserted", r.getInserted(), "elapsed", ms(t)));
        } catch (Exception e) { result.put("goodcangWarehouses", error(e)); }

        // 谷仓入库单（从2026-01-01到当前）
        try { long t = System.currentTimeMillis();
            String to = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            var r = goodcangSyncService.syncGrn("2026-01-01 00:00:00", to);
            result.put("goodcangGrn", map("inserted", r.getInserted(), "elapsed", ms(t)));
        } catch (Exception e) { result.put("goodcangGrn", error(e)); }

        // 库存流水
        try { long t = System.currentTimeMillis();
            var r = statementService.syncStatements(now.minusDays(90).toString(), now.toString());
            result.put("statement", map("inserted", r.getInserted(), "elapsed", ms(t)));
        } catch (Exception e) { result.put("statement", error(e)); }

        // 采购单
        try { long t = System.currentTimeMillis();
            var r = purchaseOrderService.sync(now.minusDays(90).toString(), now.toString());
            result.put("purchaseOrder", map("inserted", r.getInserted(), "elapsed", ms(t)));
        } catch (Exception e) { result.put("purchaseOrder", error(e)); }

        // 采购计划
        try { long t = System.currentTimeMillis();
            var r = purchasePlanQueryService.sync(now.minusDays(90).toString(), now.toString());
            result.put("purchasePlan", map("inserted", r.getInserted(), "elapsed", ms(t)));
        } catch (Exception e) { result.put("purchasePlan", error(e)); }

        result.put("total_elapsed", (System.currentTimeMillis() - totalStart) / 1000 + "s");

        // 同步完成后刷新运营数据快照
        try { overviewService.refreshSnapshot(); } catch (Exception ignored) {}

        return Result.ok(result);
    }

    /** 同步仓库库存流水 */
    @PostMapping("/statement")
    public Result<Object> syncStatement() throws Exception {
        LocalDate now = LocalDate.now();
        return Result.ok(statementService.syncStatements(now.minusDays(90).toString(), now.toString()));
    }

    /** 同步采购单：前一天 */
    @PostMapping("/purchase-order")
    public Result<Object> syncPurchaseOrder() throws Exception {
        LocalDate now = LocalDate.now();
        return Result.ok(purchaseOrderService.sync(now.minusDays(1).toString(), now.minusDays(1).toString()));
    }

    /** 首次拉取采购单：近90天 */
    @PostMapping("/purchase-order/init")
    public Result<Object> syncPurchaseOrderInit() throws Exception {
        LocalDate now = LocalDate.now();
        return Result.ok(purchaseOrderService.sync(now.minusDays(90).toString(), now.toString()));
    }

    /** 同步采购计划：前一天 */
    /** 全量刷新所有入库单详情（从本地 goodcang_grn_list 取单号，逐条拉谷仓详情） */
    @PostMapping("/goodcang-grn-detail")
    public Result<Object> syncGoodcangGrnDetail() throws Exception {
        return Result.ok(goodcangSyncService.syncAllGrnDetails());
    }

    /** 同步采购计划：前一天。 */
    @PostMapping("/purchase-plan")
    public Result<Object> syncPurchasePlan() throws Exception {
        LocalDate now = LocalDate.now();
        return Result.ok(purchasePlanQueryService.sync(now.minusDays(1).toString(), now.minusDays(1).toString()));
    }

    /** 首次拉取采购计划：近90天 */
    @PostMapping("/purchase-plan/init")
    public Result<Object> syncPurchasePlanInit() throws Exception {
        LocalDate now = LocalDate.now();
        return Result.ok(purchasePlanQueryService.sync(now.minusDays(90).toString(), now.toString()));
    }

    /** 将键值对交替的参数组装为 Map。 */
    private Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    /** 计算从 start 到现在的耗时秒数。 */
    private String ms(long start) { return (System.currentTimeMillis() - start) / 1000 + "s"; }

    /** 将异常信息包装为 Map 返回。 */
    private Map<String, Object> error(Exception e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", e.getMessage());
        return m;
    }
}
