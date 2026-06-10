package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("daily_price_tracking_cache")
public class DailyPriceTrackingCacheEntity {
    @TableId(value = "id", type = IdType.AUTO) private Long id;
    private String site; private String sku; private String productName; private String skuLevel;
    private Integer last3DaysSales; private Integer last7DaysSales; private Integer last30DaysSales; private Integer last90DaysSales;
    private Integer maxMonthlySales; private Integer overseasWarehouseStock; private Integer overseasWarehouseAge;
    private BigDecimal stockSalesRatio; private Integer estimatedReplenish;
    private BigDecimal ourLowestPrice; private BigDecimal trackingPrice; private BigDecimal trackingProfitMargin; private BigDecimal floorPrice;
    private BigDecimal returnRate; private String ebayFrontpageUrl; private String frontpageSoldUrl;
    private String brand; private String operator; private String remark; private String oeNumber;
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
