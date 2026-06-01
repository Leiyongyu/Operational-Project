package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("goodcang_grn_list")
/** 谷仓 GRN 入库单列表表 goodcang_grn_list */
public class GoodcangGrnListEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("receiving_code") private String receivingCode;
    @TableField("warehouse_code") private String warehouseCode;
    @TableField("transit_warehouse_code") private String transitWarehouseCode;
    @TableField("reference_no") private String referenceNo;
    @TableField("receiving_status") private Integer receivingStatus;
    @TableField("transit_type") private Integer transitType;
    @TableField("create_at") private LocalDateTime createAt;
    @TableField("update_at") private LocalDateTime updateAt;
    @TableField(value = "upload_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime uploadTime;
}
