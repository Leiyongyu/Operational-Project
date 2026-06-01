package com.asinking.com.openapi.mapper.mp;

import com.asinking.com.openapi.entity.InventorySnapshotEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 库存快照表 Mapper，存储定时生成的运营数据快照。 */
@Mapper
public interface InventorySnapshotMapper extends BaseMapper<InventorySnapshotEntity> {
}
