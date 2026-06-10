package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_overview")
public class InventoryOverviewEntity {
    @TableId(value = "id", type = IdType.AUTO) private Long id;
    private String warehouseNames; private String sku; private String productName; private String skuLevel;
    private BigDecimal last30DaysProfit; private BigDecimal returnRate;
    private Integer overseasOnway; private Integer overseasSellable; private Integer overseasTotal;
    private Integer purchasePendingDelivery; private Integer localSellable; private Integer localOnway;
    private String purchasePlan; private Integer lockNum; private Integer totalInventory;
    private Integer last7DaysSales; private Integer last30DaysSales; private Integer last90DaysSales;
    private Integer maxMonthlySales;
    private BigDecimal overseasInStockRatio; private BigDecimal overseasTotalRatio; private BigDecimal totalInventoryRatio;
    private String lastLocalOutboundTime; private Integer outboundDays; private Integer purchaseCycle;
    private BigDecimal purchaseQuantity; private Integer maxMonthlyReplenish; private String owner;
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
