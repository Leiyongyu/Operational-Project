package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.response.InventoryOverviewItem;
import com.asinking.com.openapi.dto.response.WarehouseOptionItem;
import com.asinking.com.openapi.interceptor.JwtAuthInterceptor;
import com.asinking.com.openapi.service.InventoryOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 运营组数据看板 — 库存概览接口。
 * admin 看全量数据，user 只看自己负责品牌的数据。
 */
@RestController
@RequestMapping("/api/inventory-overview")
public class InventoryOverviewController {

    private final InventoryOverviewService overviewService;

    /** 构造器注入库存总览服务。 */
    public InventoryOverviewController(InventoryOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    /**
     * 获取库存概览汇总，支持按 SKU / 站点 / 品牌权限筛选。
     */
    @GetMapping
    public Result<PageResult<InventoryOverviewItem>> overview(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "100") long size,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String warehouse,
            HttpServletRequest request) {
        String userId = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));
        String role = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ROLE));
        return Result.ok(overviewService.pageOverview(page, size, sku, warehouse, userId, role));
    }

    /**
     * 获取库存同步配置的仓库下拉选项。
     */
    @GetMapping("/warehouses")
    public Result<List<WarehouseOptionItem>> warehouses() {
        return Result.ok(overviewService.getWarehouseOptions());
    }

    /** 仅从本地DB重算快照，不拉取外部接口 */
    @PostMapping("/refresh-snapshot")
    public Result<String> refreshSnapshot() {
        overviewService.refreshSnapshot();
        return Result.ok("ok");
    }
}
