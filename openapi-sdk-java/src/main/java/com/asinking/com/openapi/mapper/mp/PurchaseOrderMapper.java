package com.asinking.com.openapi.mapper.mp;

import com.asinking.com.openapi.entity.PurchaseOrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** Mapper for purchase order operations. */
@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrderEntity> {
}
