package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("warehouse")
/** 仓库表 warehouse（领星同步） */
public class WarehouseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    @TableField("wid")
    private Integer wid;

    @TableField("name")
    private String name;

    @TableField("type")
    private Integer type;

    @TableField("sub_type")
    private Integer subType;

    @TableField("is_delete")
    private Integer isDelete;

    @TableField("country_code")
    private String countryCode;

    @TableField("wp_id")
    private Integer wpId;

    @TableField("wp_name")
    private String wpName;

    @TableField("t_warehouse_name")
    private String tWarehouseName;

    @TableField("t_warehouse_code")
    private String tWarehouseCode;

    @TableField("t_country_area_name")
    private String tCountryAreaName;

    @TableField("t_status")
    private Integer tStatus;

    @TableField("raw_json")
    private String rawJson;

    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
