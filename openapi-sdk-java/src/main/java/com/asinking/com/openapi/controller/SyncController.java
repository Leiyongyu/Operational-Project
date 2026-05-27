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

    public SyncController(LingxingWarehouseService warehouseService,
                          LingxingWarehouseInventoryService inventoryService,
                          LingxingWarehouseStatementService statementService,
                          LingxingPurchaseOrderService purchaseOrderService,
                          LingxingPurchasePlanQueryService purchasePlanQueryService) {
        this.warehouseService = warehouseService;
        this.inventoryService = inventoryService;
        this.statementService = statementService;
        this.purchaseOrderService = purchaseOrderService;
        this.purchasePlanQueryService = purchasePlanQueryService;
    }

    @PostMapping("/all")
    public Result<Map<String, Object>> syncAll() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        long totalStart = System.currentTimeMillis();

        // 仓库
        try { long t = System.currentTimeMillis();
            WarehouseSyncResponse r = warehouseService.syncOverseaWarehouses(new WarehouseSyncRequest());
            result.put("warehouse", map("inserted", r.getInserted(), "updated", r.getUpdated(), "elapsed", ms(t)));
        } catch (Exception e) { result.put("warehouse", error(e)); }

        // 库存
        try { long t = System.currentTimeMillis();
            WarehouseInventoryDetailSyncResponse r = inventoryService.syncAllInventoryDetails(null);
            result.put("inventory", map("inserted", r.getInserted(), "elapsed", ms(t)));
        } catch (Exception e) { result.put("inventory", error(e)); }

        result.put("total_elapsed", (System.currentTimeMillis() - totalStart) / 1000 + "s");
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

    private Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    private String ms(long start) { return (System.currentTimeMillis() - start) / 1000 + "s"; }

    private Map<String, Object> error(Exception e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", e.getMessage());
        return m;
    }
}
