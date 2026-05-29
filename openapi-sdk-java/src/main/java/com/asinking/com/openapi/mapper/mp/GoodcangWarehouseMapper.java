package com.asinking.com.openapi.mapper.mp;

import com.asinking.com.openapi.entity.GoodcangWarehouseEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Update;

/** Mapper for Goodcang warehouse operations. */
@Mapper
public interface GoodcangWarehouseMapper extends BaseMapper<GoodcangWarehouseEntity> {

    @Update("UPDATE goodcang_warehouse gw INNER JOIN warehouse w ON " +
            "(w.name LIKE '%德国%' AND gw.warehouse_name LIKE '%德国%') OR " +
            "(w.name LIKE '%英国%' AND gw.warehouse_name LIKE '%英国%') OR " +
            "(w.name LIKE '%新泽西%' AND gw.warehouse_name LIKE '%新泽西%') OR " +
            "(w.name LIKE '%加州%' AND gw.warehouse_name LIKE '%加州%') " +
            "SET gw.wid = w.wid WHERE gw.wid = 0")
    int fillWidByFuzzyMatch();
}
