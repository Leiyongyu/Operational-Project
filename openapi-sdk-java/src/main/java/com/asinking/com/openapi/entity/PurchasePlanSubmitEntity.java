package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("purchase_plan_submit")
public class PurchasePlanSubmitEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("sku") private String sku;
    @TableField("wid") private Integer wid;
    @TableField("warehouse_name") private String warehouseName;
    @TableField("quantity_plan") private Integer quantityPlan;
    @TableField("quantity_replenish") private Integer quantityReplenish;
    @TableField("quantity_purchase") private java.math.BigDecimal quantityPurchase;
    @TableField("remark") private String remark;
    @TableField("expect_arrive_time") private String expectArriveTime;
    @TableField("sid") private String sid;
    @TableField("supplier_id") private Integer supplierId;
    @TableField("fnsku") private String fnsku;
    @TableField("purchaser_id") private Integer purchaserId;
    @TableField("plan_sn") private String planSn;
    @TableField("ppg_sn") private String ppgSn;
    @TableField("status_text") private String statusText;
    @TableField("creator_owner_name") private String creatorOwnerName;
    @TableField("creator_account") private String creatorAccount;
    @TableField("creator_role") private String creatorRole;
    @TableField("approver") private String approver;
    @TableField("approve_time") private LocalDateTime approveTime;

    @TableField(value = "submit_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime submitTime;
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
