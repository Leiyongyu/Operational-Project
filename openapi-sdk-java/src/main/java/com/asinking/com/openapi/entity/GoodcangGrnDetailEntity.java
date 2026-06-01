package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("goodcang_grn_detail")
/** 谷仓 GRN 入库单明细表 goodcang_grn_detail */
public class GoodcangGrnDetailEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("receiving_code") private String receivingCode;
    @TableField("product_sku") private String productSku;
    @TableField("box_no") private String boxNo;
    @TableField("transit_pre_count") private Integer transitPreCount;
    @TableField("transit_receiving_count") private Integer transitReceivingCount;
    @TableField("reference_box_no") private String referenceBoxNo;
    @TableField(value = "upload_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime uploadTime;
}
