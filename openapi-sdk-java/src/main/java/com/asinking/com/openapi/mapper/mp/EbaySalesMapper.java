package com.asinking.com.openapi.mapper.mp;

import com.asinking.com.openapi.entity.EbaySalesEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** Mapper for eBay sales data operations. */
@Mapper
public interface EbaySalesMapper extends BaseMapper<EbaySalesEntity> {
}
