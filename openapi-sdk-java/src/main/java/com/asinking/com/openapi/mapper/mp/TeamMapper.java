package com.asinking.com.openapi.mapper.mp;

import com.asinking.com.openapi.entity.TeamEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 团队表 Mapper，存储组长与组员关联关系。 */
@Mapper
public interface TeamMapper extends BaseMapper<TeamEntity> {
}
