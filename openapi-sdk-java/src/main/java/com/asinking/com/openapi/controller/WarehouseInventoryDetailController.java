package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.request.WarehouseInventoryDetailFullSyncRequest;
import com.asinking.com.openapi.dto.request.WarehouseInventoryDetailSyncRequest;
import com.asinking.com.openapi.dto.response.WarehouseInventoryDetailSyncResponse;
import com.asinking.com.openapi.entity.WarehouseInventoryDetailEntity;
import com.asinking.com.openapi.service.LingxingWarehouseInventoryService;
import com.asinking.com.openapi.service.WarehouseInventoryDetailService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仓库库存明细接口，提供库存同步和本地分页查询。
 * /sync 按指定 wid 同步单页，/sync-all 从 DB 读取所有 wid 后全量同步（先删后插）。
 */
@RestController
@RequestMapping("/api/warehouse/inventory-details")
public class WarehouseInventoryDetailController {

    private final LingxingWarehouseInventoryService lingxingWarehouseInventoryService;
    private final WarehouseInventoryDetailService warehouseInventoryDetailService;

    /** 构造器注入领星库存同步服务和本地库存明细查询服务。 */
    public WarehouseInventoryDetailController(LingxingWarehouseInventoryService lingxingWarehouseInventoryService,
                                             WarehouseInventoryDetailService warehouseInventoryDetailService) {
        this.lingxingWarehouseInventoryService = lingxingWarehouseInventoryService;
        this.warehouseInventoryDetailService = warehouseInventoryDetailService;
    }

    /**
     * 按指定 wid 同步库存明细（增量 upsert）。
     *
     * @param req wid 仓库ID(必填), offset/length 分页, sku 可选筛选
     */
    @PostMapping("/sync")
    public Result<Object> sync(@RequestBody WarehouseInventoryDetailSyncRequest req) throws Exception {
        return Result.ok(lingxingWarehouseInventoryService.syncInventoryDetails(req));
    }

    /**
     * 全量同步库存明细：先清空表，再从 DB 读取海外仓 wid，逐页拉取并批量插入。
     *
     * @param req length 分页大小(可选，默认200), sku 可选筛选
     */
    @PostMapping("/sync-all")
    public Result<Object> syncAll(@RequestBody(required = false) WarehouseInventoryDetailFullSyncRequest req) throws Exception {
        return Result.ok(lingxingWarehouseInventoryService.syncAllInventoryDetails(req));
    }

    /**
     * 分页查询本地库存明细，支持按 wid / productId / sku 筛选。
     */
    @GetMapping
    public Result<PageResult<WarehouseInventoryDetailEntity>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer wid,
            @RequestParam(required = false) Integer productId,
            @RequestParam(required = false) String sku) {
        long p = page <= 0 ? 1 : page;
        long s = size <= 0 ? 10 : Math.min(size, 200);

        Page<WarehouseInventoryDetailEntity> mpPage = new Page<>(p, s);
        Page<WarehouseInventoryDetailEntity> result = warehouseInventoryDetailService.page(mpPage, warehouseInventoryDetailService.lambdaQuery()
                .eq(wid != null, WarehouseInventoryDetailEntity::getWid, wid)
                .eq(productId != null, WarehouseInventoryDetailEntity::getProductId, productId)
                .like(StringUtils.hasText(sku), WarehouseInventoryDetailEntity::getSku, sku)
                .getWrapper());

        return Result.ok(new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords()));
    }
}
