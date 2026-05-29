package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.request.WarehouseSyncRequest;
import com.asinking.com.openapi.entity.WarehouseEntity;
import com.asinking.com.openapi.service.LingxingWarehouseService;
import com.asinking.com.openapi.service.WarehouseService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 海外仓管理接口，提供仓库同步和分页查询。
 * 同步为增量 upsert（wid 唯一键），仅拉取 type=3 海外仓。
 */
@RestController
@RequestMapping("/api/warehouse/warehouses")
public class WarehouseController {

    private final LingxingWarehouseService lingxingWarehouseService;
    private final WarehouseService warehouseService;

    /** 构造器注入领星仓库同步服务和本地仓库查询服务。 */
    public WarehouseController(LingxingWarehouseService lingxingWarehouseService, WarehouseService warehouseService) {
        this.lingxingWarehouseService = lingxingWarehouseService;
        this.warehouseService = warehouseService;
    }

    /**
     * 从领星同步海外仓数据到本地库（增量 upsert）。
     *
     * @param req offset 分页偏移, length 分页长度（可选）
     */
    @PostMapping("/sync")
    public Result<Object> sync(@RequestBody(required = false) WarehouseSyncRequest req) throws Exception {
        return Result.ok(lingxingWarehouseService.syncOverseaWarehouses(req));
    }

    /**
     * 分页查询本地已同步的仓库列表（type=1 本地仓 + type=3 海外仓，is_delete=0）。
     */
    @GetMapping
    public Result<PageResult<WarehouseEntity>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer wid,
            @RequestParam(required = false) String warehouseName) {
        long p = page <= 0 ? 1 : page;
        long s = size <= 0 ? 10 : Math.min(size, 200);

        Page<WarehouseEntity> mpPage = new Page<>(p, s);
        Page<WarehouseEntity> result = warehouseService.page(mpPage, warehouseService.lambdaQuery()
                .eq(wid != null, WarehouseEntity::getWid, wid)
                .like(StringUtils.hasText(warehouseName), WarehouseEntity::getName, warehouseName)
                .eq(WarehouseEntity::getIsDelete, 0)
                .getWrapper());
        return Result.ok(new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords()));
    }
}
