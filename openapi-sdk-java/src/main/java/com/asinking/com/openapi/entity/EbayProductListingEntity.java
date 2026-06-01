package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ebay_product_listing")
/** eBay 商品刊登表 ebay_product_listing */
public class EbayProductListingEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String platform;

    private String itemId;

    private String itemUrl;

    private String pictureUrl;

    private String msku;

    private String sku;

    private String localSku;

    private String title;

    private String localName;

    private String attribute;

    private Integer listingType;

    private String listingTypeName;

    private Integer listingStatus;

    private String listingStatusName;

    private BigDecimal price;

    private BigDecimal startPrice;

    private BigDecimal acceptPrice;

    private Integer quantity;

    private Integer autoRestock;

    private String productAutoRestockResponse;

    private String location;

    private Integer dispatchTimeMax;

    private LocalDateTime listingStartTime;

    private LocalDateTime listingEndTime;

    private String storeId;

    private String storeName;

    private String siteCode;

    private String siteName;

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
