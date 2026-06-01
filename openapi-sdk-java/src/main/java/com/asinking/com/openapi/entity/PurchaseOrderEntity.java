package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("purchase_order")
/** 采购订单表 purchase_order（领星同步） */
public class PurchaseOrderEntity {
    @TableId(value = "id", type = IdType.INPUT) private String id;
    @TableField("order_sn") private String orderSn;
    @TableField("custom_order_sn") private String customOrderSn;
    @TableField("supplier_id") private Integer supplierId;
    @TableField("supplier_name") private String supplierName;
    @TableField("opt_uid") private Integer optUid;
    @TableField("opt_realname") private String optRealname;
    @TableField("auditor_realname") private String auditorRealname;
    @TableField("last_realname") private String lastRealname;
    @TableField("create_time") private LocalDateTime createTime;
    @TableField("order_time") private LocalDateTime orderTime;
    @TableField("update_time") private String updateTime;
    @TableField("status") private Integer status;
    @TableField("status_text") private String statusText;
    @TableField("wid") private Integer wid;
    @TableField("ware_house_name") private String wareHouseName;
    @TableField("item_sku") private String itemSku;
    @TableField("item_product_name") private String itemProductName;
    @TableField("item_product_id") private Integer itemProductId;
    @TableField("item_quantity_real") private Integer itemQuantityReal;
    @TableField("item_quantity_entry") private Integer itemQuantityEntry;
    @TableField("item_quantity_receive") private Integer itemQuantityReceive;
    @TableField("item_price") private java.math.BigDecimal itemPrice;
    @TableField("item_amount") private java.math.BigDecimal itemAmount;
    @TableField(value = "upload_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime uploadTime;
}
