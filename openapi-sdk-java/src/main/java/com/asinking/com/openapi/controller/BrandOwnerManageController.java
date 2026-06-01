package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.request.BrandOwnerCreateRequest;
import com.asinking.com.openapi.dto.request.BrandOwnerUpdateRequest;
import com.asinking.com.openapi.dto.response.BrandOwnerResponse;
import com.asinking.com.openapi.service.BrandOwnerService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 品牌归属管理接口，提供品牌归属的增删改查及分页查询。
 */
@RestController
@RequestMapping("/api/brand-owners")
public class BrandOwnerManageController {

    private final BrandOwnerService brandOwnerService;

    /** 构造器注入品牌负责人服务。 */
    public BrandOwnerManageController(BrandOwnerService brandOwnerService) {
        this.brandOwnerService = brandOwnerService;
    }

    /**
     * 新增品牌归属记录。
     *
     * @param req brandCode 品牌编码, ownerName 归属人名称
     */
    @PostMapping
    public Result<BrandOwnerResponse> create(@RequestBody BrandOwnerCreateRequest req) {
        return Result.ok(brandOwnerService.create(req));
    }

    /**
     * 根据 ID 更新品牌归属。
     *
     * @param id  品牌归属主键 ID
     * @param req 支持部分更新，至少传 brandCode 或 ownerName 之一
     */
    @PutMapping("/{id}")
    public Result<BrandOwnerResponse> update(@PathVariable Integer id, @RequestBody BrandOwnerUpdateRequest req) {
        return Result.ok(brandOwnerService.update(id, req));
    }

    /**
     * 根据 ID 查询单条品牌归属。
     */
    @GetMapping("/{id}")
    public Result<BrandOwnerResponse> detail(@PathVariable Integer id) {
        return Result.ok(brandOwnerService.detail(id));
    }

    /**
     * 分页查询品牌归属列表，支持按 brandCode / ownerName 模糊搜索。
     */
    @GetMapping
    public Result<PageResult<BrandOwnerResponse>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String brandCode,
            @RequestParam(required = false) String ownerName) {
        return Result.ok(brandOwnerService.page(page, size, brandCode, ownerName));
    }

    /**
     * 查询所有去重的品牌编码列表。
     */
    @GetMapping("/distinct-brand-codes")
    public Result<List<String>> distinctBrandCodes() {
        return Result.ok(brandOwnerService.listDistinctBrandCodes());
    }

    /**
     * 查询所有去重的负责人名称列表。
     */
    @GetMapping("/distinct-owner-names")
    public Result<List<String>> distinctOwnerNames() {
        return Result.ok(brandOwnerService.listDistinctOwnerNames());
    }

    /**
     * 根据 ID 删除品牌归属。
     */
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Integer id) {
        brandOwnerService.removeById(id);
        return Result.ok(java.util.Collections.singletonMap("success", true));
    }
}
