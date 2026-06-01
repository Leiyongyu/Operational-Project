package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("warehouse_inventory_detail")
/** 仓库库存明细表 warehouse_inventory_detail */
public class WarehouseInventoryDetailEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    @TableField("wid")
    private Integer wid; // 仓库id

    @TableField("product_id")
    private Integer productId; // 本地产品id

    @TableField("sku")
    private String sku; // SKU

    @TableField("seller_id")
    private String sellerId; // 店铺id

    @TableField("fnsku")
    private String fnsku; // FNSKU

    @TableField("product_total")
    private Integer productTotal; // 实际库存总量(可用量+次品量+待检待上架量+锁定量)

    @TableField("product_valid_num")
    private Integer productValidNum; // 可用量

    @TableField("product_bad_num")
    private Integer productBadNum; // 次品量

    @TableField("product_qc_num")
    private Integer productQcNum; // 待检待上架量

    @TableField("product_lock_num")
    private Integer productLockNum; // 锁定量

    @TableField("good_lock_num")
    private Integer goodLockNum; // 良品锁定数

    @TableField("bad_lock_num")
    private Integer badLockNum; // 不良品锁定数

    @TableField("stock_cost_total")
    private BigDecimal stockCostTotal; // 库存成本

    @TableField("quantity_receive")
    private BigDecimal quantityReceive; // 待到货量

    @TableField("stock_cost")
    private BigDecimal stockCost; // 单位库存成本

    @TableField("product_onway")
    private Integer productOnway; // 调拨在途

    @TableField("transit_head_cost")
    private BigDecimal transitHeadCost; // 调拨在途头程成本

    @TableField("average_age")
    private Integer averageAge; // 平均库龄

    @TableField("expect_valid_num")
    private Integer expectValidNum; // 海外仓预期有效数

    @TableField("expect_pending_num")
    private BigDecimal expectPendingNum; // 海外仓预期待处理数

    @TableField("available_inventory_box_qty")
    private Integer availableInventoryBoxQty; // 海外仓可用箱库存

    @TableField("purchase_price")
    private BigDecimal purchasePrice; // 采购单价

    @TableField("price")
    private BigDecimal price; // 单位费用

    @TableField("head_stock_price")
    private BigDecimal headStockPrice; // 单位头程

    @TableField("stock_price")
    private BigDecimal stockPrice; // 单位库存成本

    @TableField("third_inventory_json")
    private String thirdInventoryJson; // 海外仓第三方库存信息(JSON)

    @TableField("stock_age_list_json")
    private String stockAgeListJson; // 库龄信息(JSON)

    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
