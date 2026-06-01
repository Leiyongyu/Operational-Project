package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("profit_report")
/** 利润报表表 profit_report */
public class ProfitReportEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("file_name") private String fileName;
    @TableField("msku") private String msku;
    @TableField("platform") private String platform;
    @TableField("store_name") private String storeName;
    @TableField("country_code") private String countryCode;
    @TableField("currency_code") private String currencyCode;
    @TableField("volume") private Integer volume;
    @TableField("sales_amount") private BigDecimal salesAmount;
    @TableField("gross_profit") private BigDecimal grossProfit;
    @TableField("gross_margin") private BigDecimal grossMargin;
    @TableField("purchase_cost") private BigDecimal purchaseCost;
    @TableField("logistics_cost") private BigDecimal logisticsCost;
    @TableField("ship_time") private String shipTime;
    @TableField(value = "upload_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime uploadTime;
}
