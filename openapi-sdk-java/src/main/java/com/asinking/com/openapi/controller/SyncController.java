package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.common.annotation.OperationLog;
import com.asinking.com.openapi.dto.request.WarehouseSyncRequest;
import com.asinking.com.openapi.dto.response.WarehouseInventoryDetailSyncResponse;
import com.asinking.com.openapi.dto.response.WarehouseSyncResponse;
import com.asinking.com.openapi.service.LingxingWarehouseInventoryService;
import com.asinking.com.openapi.service.LingxingWarehouseService;
import com.asinking.com.openapi.service.LingxingPurchaseOrderService;
import com.asinking.com.openapi.service.LingxingWarehouseStatementService;
import com.asinking.com.openapi.service.LingxingPurchasePlanQueryService;
import com.asinking.com.openapi.service.LingxingEbayService;
import com.asinking.com.openapi.service.GoodcangProductService;
import com.asinking.com.openapi.service.GoodcangSyncService;
import com.asinking.com.openapi.service.InventoryOverviewService;
import com.asinking.com.openapi.service.OperationLogService;
import com.asinking.com.openapi.utils.JwtTokenService;
import com.alibaba.fastjson2.JSON;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private final GoodcangProductService goodcangProductService;
    private final InventoryOverviewService overviewService;
    private final OperationLogService logService;
    private final HttpServletRequest request;
    private final JwtTokenService jwtTokenService;
    private final RedissonClient redissonClient;

    public SyncController(LingxingWarehouseService warehouseService,
                          LingxingWarehouseInventoryService inventoryService,
                          LingxingWarehouseStatementService statementService,
                          LingxingPurchaseOrderService purchaseOrderService,
                          LingxingPurchasePlanQueryService purchasePlanQueryService,
                          LingxingEbayService ebayService,
                          GoodcangSyncService goodcangSyncService,
                          GoodcangProductService goodcangProductService,
                          InventoryOverviewService overviewService,
                          OperationLogService logService,
                          JwtTokenService jwtTokenService,
                          RedissonClient redissonClient,
                          HttpServletRequest request) {
        this.warehouseService = warehouseService;
        this.inventoryService = inventoryService;
        this.statementService = statementService;
        this.purchaseOrderService = purchaseOrderService;
        this.purchasePlanQueryService = purchasePlanQueryService;
        this.ebayService = ebayService;
        this.goodcangSyncService = goodcangSyncService;
        this.goodcangProductService = goodcangProductService;
        this.overviewService = overviewService;
        this.logService = logService;
        this.jwtTokenService = jwtTokenService;
        this.redissonClient = redissonClient;
        this.request = request;
    }

    private String getCurrentUser() {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtTokenService.parse(auth.substring(7)).getPayload().getSubject(); }
            catch (Exception ignored) {}
        }
        return "SYSTEM";
    }

    /** 一键全量同步：Redis分布式锁防重，认证失败自动跳过后续步骤 */
    @PostMapping("/all")
    public Result<Map<String, Object>> syncAll() throws Exception {
        RLock lock = redissonClient.getLock("sync:all");
        if (!lock.tryLock(0, 10, TimeUnit.MINUTES)) {
            return Result.fail(com.asinking.com.openapi.common.response.ResultCode.BAD_REQUEST, "同步任务正在执行中，请稍后再试");
        }
        try {
        Map<String, Object> result = new LinkedHashMap<>();
        long totalStart = System.currentTimeMillis();
        LocalDate now = LocalDate.now();
        String apiPath = "/api/sync/all";
        String operator = getCurrentUser();
        String ip = getClientIp();
        // 认证失败标记：一个领星接口认证失败，后续领星接口直接跳过
        boolean[] authFailed = {false};

        // 仓库
        result.put("warehouse", runStepWithAuth("同步外服", "领星-仓库信息", apiPath, operator, ip, () -> {
            long t = System.currentTimeMillis();
            WarehouseSyncResponse r = warehouseService.syncOverseaWarehouses(new WarehouseSyncRequest());
            return map("inserted", r.getInserted(), "updated", r.getUpdated(), "elapsed", ms(t));
        }, authFailed));

        // eBay商品
        result.put("ebayItems", runStepWithAuth("同步外服", "领星-eBay商品刊登", apiPath, operator, ip, () -> {
            long t = System.currentTimeMillis();
            var r = ebayService.syncAllEbayItems(null);
            return map("total", r.getRemoteTotal(), "elapsed", ms(t));
        }, authFailed));

        // 库存
        result.put("inventory", runStepWithAuth("同步外服", "领星-库存明细", apiPath, operator, ip, () -> {
            long t = System.currentTimeMillis();
            WarehouseInventoryDetailSyncResponse r = inventoryService.syncAllInventoryDetails(null);
            return map("inserted", r.getInserted(), "elapsed", ms(t));
        }, authFailed));

        // 谷仓仓库
        result.put("goodcangWarehouses", runStepWithAuth("同步外服", "谷仓-仓库信息", apiPath, operator, ip, () -> {
            long t = System.currentTimeMillis();
            var r = goodcangSyncService.syncWarehouses();
            return map("inserted", r.getInserted(), "elapsed", ms(t));
        }, authFailed));

        // 谷仓入库单
        result.put("goodcangGrn", runStepWithAuth("同步外服", "谷仓-入库单", apiPath, operator, ip, () -> {
            long t = System.currentTimeMillis();
            String to = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            var r = goodcangSyncService.syncGrn("2026-01-01 00:00:00", to);
            return map("inserted", r.getInserted(), "elapsed", ms(t));
        }, authFailed));

        // 库存流水
        result.put("statement", runStepWithAuth("同步外服", "领星-库存流水", apiPath, operator, ip, () -> {
            long t = System.currentTimeMillis();
            var r = statementService.syncStatements(now.minusDays(90).toString(), now.toString());
            return map("inserted", r.getInserted(), "elapsed", ms(t));
        }, authFailed));

        // 采购单
        result.put("purchaseOrder", runStepWithAuth("同步外服", "领星-采购单", apiPath, operator, ip, () -> {
            long t = System.currentTimeMillis();
            var r = purchaseOrderService.sync(now.minusDays(90).toString(), now.toString());
            return map("inserted", r.getInserted(), "elapsed", ms(t));
        }, authFailed));

        // 谷仓商品信息同步
        result.put("goodcangProducts", runStepWithAuth("同步外服", "谷仓-商品信息", apiPath, operator, ip, () -> {
            long t = System.currentTimeMillis();
            var r = goodcangProductService.syncFromApi();
            return map("total", intValObj(r, "total"), "inserted", intValObj(r, "inserted"), "updated", intValObj(r, "updated"), "skipped", intValObj(r, "skipped"), "elapsed", ms(t));
        }, authFailed));

        result.put("purchasePlan", runStepWithAuth("同步外服", "领星-采购计划", apiPath, operator, ip, () -> {
            long t = System.currentTimeMillis();
            var r = purchasePlanQueryService.sync(now.minusDays(90).toString(), now.toString());
            return map("inserted", r.getInserted(), "elapsed", ms(t));
        }, authFailed));

        result.put("total_elapsed", (System.currentTimeMillis() - totalStart) / 1000 + "s");
        try { overviewService.refreshSnapshot(); } catch (Exception ignored) {}

        return Result.ok(result);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private boolean isAuthError(String msg) {
        if (msg == null) return false;
        msg = msg.toLowerCase();
        return msg.contains("token") || msg.contains("登录") || msg.contains("expired") || msg.contains("auth") || msg.contains("401") || msg.contains("403");
    }

    private Map<String, Object> runStepWithAuth(String opType, String target, String apiPath,
                                                  String operator, String ip, StepRunner runner,
                                                  boolean[] authFailed) {
        if (authFailed[0]) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("step_target", target); details.put("step_status", "已跳过(登录过期)");
            logService.log(apiPath, "POST", operator, ip, opType, target, "已跳过", 0, 0, 0, "前序认证失败", JSON.toJSONString(details));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("inserted", 0); r.put("updated", 0); r.put("elapsed", "0s"); return r;
        }
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> stepResult = runner.run();
            int inserted = intVal(stepResult, "inserted");
            int updated = intVal(stepResult, "updated");
            int skipped = intVal(stepResult, "skipped");
            int total = intVal(stepResult, "total");
            String elapsed = (System.currentTimeMillis() - start) / 1000.0 + "s";
            stepResult.put("elapsed", elapsed);
            Map<String, Object> details = new LinkedHashMap<>(stepResult);
            details.put("step_target", target); details.put("step_status", "成功"); details.put("step_elapsed", elapsed);
            logService.log(apiPath, "POST", operator, ip, opType, target,
                    skipped > 0 ? "成功(有跳过)" : "成功", total > 0 ? total : inserted + updated + skipped,
                    inserted + updated, skipped, null, JSON.toJSONString(details));
            return stepResult;
        } catch (Exception e) {
            String elapsed = (System.currentTimeMillis() - start) / 1000.0 + "s";
            if (isAuthError(e.getMessage())) authFailed[0] = true;
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("step_target", target); details.put("step_status", "失败"); details.put("step_elapsed", elapsed);
            details.put("error", e.getMessage());
            logService.log(apiPath, "POST", operator, ip, opType, target, "失败", 0, 0, 0, e.getMessage(), JSON.toJSONString(details));
            return error(e);
        }
    }

    @FunctionalInterface
    private interface StepRunner { Map<String, Object> run() throws Exception; }

    private int intValObj(Map<String, Object> m, String key) { return intVal(m, key); }

    private int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }

    private String getClientIp() {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty()) return xri;
        return request.getRemoteAddr();
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
