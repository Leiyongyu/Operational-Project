package com.asinking.com.openapi.service;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.entity.PurchasePlanSubmitEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface PurchasePlanSubmitService extends IService<PurchasePlanSubmitEntity> {

    int batchSubmit(List<PurchasePlanSubmitEntity> items);

    PageResult<PurchasePlanSubmitEntity> page(long page, long size, String account, String role, String ownerName,
                                              String sku, String creator);
}
