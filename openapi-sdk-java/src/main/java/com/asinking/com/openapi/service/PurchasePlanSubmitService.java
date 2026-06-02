package com.asinking.com.openapi.service;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.entity.PurchasePlanSubmitEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 采购计划提交服务接口：批量提交、分页查询（支持权限过滤）。
 */
public interface PurchasePlanSubmitService extends IService<PurchasePlanSubmitEntity> {

    /** 批量插入采购计划记录，自动生成 UUID 主键。 */
    int batchSubmit(List<PurchasePlanSubmitEntity> items);

    /** 分页查询，按角色权限过滤（admin 看全部，组长看组员，组员看自己），支持按状态筛选。 */
    PageResult<PurchasePlanSubmitEntity> page(long page, long size, String account, String role, String ownerName,
                                              String sku, String creator, String status);
}
