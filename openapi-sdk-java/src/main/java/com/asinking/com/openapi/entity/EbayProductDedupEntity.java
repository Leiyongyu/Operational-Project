package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * eBay 商品去重表 ebay_product_dedup。
 * 从 ebay_product_listing 按 (site, sku) 去重后存储，OE 号由用户维护。
 */
@Data
@TableName("ebay_product_dedup")
public class EbayProductDedupEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("site")
    private String site;

    @TableField("sku")
    private String sku;

    @TableField("oe_number")
    private String oeNumber;

    @TableField("product_name")
    private String productName;

    @TableField("remark")
    private String remark;

    @TableField("tracking_price")
    private java.math.BigDecimal trackingPrice;

    @TableField("tracking_profit_margin")
    private java.math.BigDecimal trackingProfitMargin;

    @TableField("floor_price")
    private java.math.BigDecimal floorPrice;

    @TableField("profit_rate")
    private java.math.BigDecimal profitRate;

    @TableField("return_rate")
    private java.math.BigDecimal returnRate;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
