package com.asinking.com.openapi.service;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.dto.request.BrandOwnerCreateRequest;
import com.asinking.com.openapi.dto.request.BrandOwnerUpdateRequest;
import com.asinking.com.openapi.dto.response.BrandOwnerResponse;
import com.asinking.com.openapi.entity.BrandOwnerEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 品牌归属业务接口，管理 brand_owner 表的 CRUD 及分页查询。
 */
public interface BrandOwnerService extends IService<BrandOwnerEntity> {

    /** 创建品牌与负责人关联记录 */
    BrandOwnerResponse create(BrandOwnerCreateRequest req);

    /** 根据 ID 更新品牌负责人信息 */
    BrandOwnerResponse update(Integer id, BrandOwnerUpdateRequest req);

    /** 根据 ID 查询品牌负责人详情 */
    BrandOwnerResponse detail(Integer id);

    /** 分页查询品牌负责人列表，支持品牌编码和负责人名称筛选 */
    PageResult<BrandOwnerResponse> page(long page, long size, String brandCode, String ownerName);

    /** 查询所有去重的品牌编码 */
    List<String> listDistinctBrandCodes();

    /** 查询所有去重的负责人名称 */
    List<String> listDistinctOwnerNames();
}
