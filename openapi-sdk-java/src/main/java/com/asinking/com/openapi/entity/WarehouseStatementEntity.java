package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("warehouse_statement")
/** 仓库库存流水表 warehouse_statement */
public class WarehouseStatementEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("statement_id") private String statementId;
    @TableField("wid") private Integer wid;
    @TableField("ware_house_name") private String wareHouseName;
    @TableField("order_sn") private String orderSn;
    @TableField("ref_order_sn") private String refOrderSn;
    @TableField("sku") private String sku;
    @TableField("seller_id") private String sellerId;
    @TableField("fnsku") private String fnsku;
    @TableField("opt_time") private LocalDateTime optTime;
    @TableField("type") private Integer type;
    @TableField("type_text") private String typeText;
    @TableField("sub_type") private String subType;
    @TableField("sub_type_text") private String subTypeText;
    @TableField("product_name") private String productName;
    @TableField("product_good_num") private Integer productGoodNum;
    @TableField("product_bad_num") private Integer productBadNum;
    @TableField(value = "upload_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime uploadTime;
}
