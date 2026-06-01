package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ebay_shop_list")
/** eBay 店铺列表表 ebay_shop_list */
public class EbayShopListEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String storeId;

    private String sid;

    private String storeName;

    private String platformCode;

    private String platformName;

    private String currency;

    private Integer isSync;

    private Integer status;

    private String countryCode;

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
