package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 每日跟价最低价格记录表 lowest_price_record。
 * 按 (site, sku) 唯一，存储从 Excel 导入的最低价格。
 */
@Data
@TableName("lowest_price_record")
public class LowestPriceRecordEntity {

    /** UUID 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 站点 */
    @TableField("site")
    private String site;

    /** SKU */
    @TableField("sku")
    private String sku;

    /** eBay Item Number（最低价对应的商品编号） */
    @TableField("item_number")
    private String itemNumber;

    /** 最低价格 */
    @TableField("lowest_price")
    private BigDecimal lowestPrice;

    /** 最近一次上传时间 */
    @TableField("upload_time")
    private LocalDateTime uploadTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
