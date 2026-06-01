package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ebay_sales")
/** eBay 销量表 ebay_sales */
public class EbaySalesEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("platform_order_no") private String platformOrderNo;
    @TableField("currency") private String currency;
    @TableField("sku") private String sku;
    @TableField("quantity") private Integer quantity;
    @TableField("payment_time") private LocalDateTime paymentTime;

    @TableField(value = "upload_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime uploadTime;
}
