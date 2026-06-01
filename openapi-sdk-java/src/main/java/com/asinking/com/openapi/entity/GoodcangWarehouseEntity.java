package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("goodcang_warehouse")
/** 谷仓仓库表 goodcang_warehouse */
public class GoodcangWarehouseEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("warehouse_code") private String warehouseCode;
    @TableField("wid") private Integer wid;
    @TableField("warehouse_name") private String warehouseName;
    @TableField("country_code") private String countryCode;
    @TableField("wp_code") private String wpCode;
    @TableField("wp_name") private String wpName;
    @TableField("state") private String state;
    @TableField("city") private String city;
    @TableField("postcode") private String postcode;
    @TableField("contacter") private String contacter;
    @TableField("phone") private String phone;
    @TableField("street_address1") private String streetAddress1;
    @TableField("street_address2") private String streetAddress2;
    @TableField("street_number") private String streetNumber;
    @TableField(value = "upload_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime uploadTime;
}
