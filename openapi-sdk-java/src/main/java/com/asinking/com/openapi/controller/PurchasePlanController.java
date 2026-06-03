package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.response.InventoryOverviewItem;
import com.asinking.com.openapi.dto.response.PurchasePlanCreateResponse;
import com.asinking.com.openapi.entity.WarehouseEntity;
import com.asinking.com.openapi.mapper.mp.EbayShopListMapper;
import com.asinking.com.openapi.service.EbayProductDedupService;
import com.asinking.com.openapi.service.InventoryOverviewService;
import com.asinking.com.openapi.service.LingxingPurchasePlanService;
import com.asinking.com.openapi.service.WarehouseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 采购计划接口，提供 Excel 上传/JSON 创建计划，以及 SKU/店铺/仓库的搜索提示。
 */
@RestController
@RequestMapping("/api/purchase-plan")
public class PurchasePlanController {

    private final LingxingPurchasePlanService service;
    private final EbayShopListMapper ebayShopMapper;
    private final WarehouseService warehouseService;
    private final InventoryOverviewService overviewService;
    private final EbayProductDedupService dedupService;

    public PurchasePlanController(LingxingPurchasePlanService service,
                                  EbayShopListMapper ebayShopMapper,
                                  WarehouseService warehouseService,
                                  InventoryOverviewService overviewService,
                                  EbayProductDedupService dedupService) {
        this.service = service;
        this.ebayShopMapper = ebayShopMapper;
        this.warehouseService = warehouseService;
        this.overviewService = overviewService;
        this.dedupService = dedupService;
    }

    /** 上传 Excel 文件并创建采购计划。 */
    @PostMapping("/upload")
    public Result<PurchasePlanCreateResponse> upload(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.ok(service.uploadAndCreate(file));
    }

    /** 根据 JSON 数据批量创建采购计划。 */
    @PostMapping("/create")
    public Result<PurchasePlanCreateResponse> create(@RequestBody List<Map<String, Object>> data) throws Exception {
        return Result.ok(service.createFromJson(data));
    }

    /** 从去重表搜索 SKU，已去重，直接返回。 */
    @GetMapping("/skus")
    public Result<List<Map<String, Object>>> searchSkus(@RequestParam(defaultValue = "") String keyword) {
        Set<String> skus = new LinkedHashSet<>();
        for (var e : dedupService.listAll()) {
            String s = e.getSku();
            if (s == null || s.isEmpty()) continue;
            if (keyword.isEmpty() || s.toLowerCase().contains(keyword.toLowerCase()))
                skus.add(s);
        }
        return Result.ok(skus.stream().limit(500).map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sku", s);
            return m;
        }).collect(Collectors.toList()));
    }

    /** 从 eBay 店铺表中搜索店铺名，用于前端下拉提示。 */
    @GetMapping("/stores")
    public Result<List<Map<String, Object>>> searchStores(@RequestParam(defaultValue = "") String keyword) {
        Map<String, String> storeMap = new LinkedHashMap<>();
        for (var e : ebayShopMapper.selectList(null)) {
            String sid = e.getSid(), name = e.getStoreName();
            if (name != null && !name.isEmpty() && !storeMap.containsKey(name)
                    && (keyword.isEmpty() || name.toLowerCase().contains(keyword.toLowerCase())))
                storeMap.put(name, sid != null ? sid : "");
        }
        return Result.ok(storeMap.entrySet().stream().limit(50).map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sid", e.getValue());
            m.put("seller_name", e.getKey());
            return m;
        }).collect(Collectors.toList()));
    }

    /** 返回三个 CTUeBay 中转仓库：DE/US/UK。 */
    @GetMapping("/warehouses")
    public Result<List<Map<String, Object>>> searchWarehouses(@RequestParam(defaultValue = "") String keyword) {
        List<Integer> allowedWids = Arrays.asList(18676, 18675, 18674);
        List<Map<String, Object>> list = new ArrayList<>();
        for (WarehouseEntity wh : warehouseService.listByWids(allowedWids)) {
            String name = wh.getName();
            if (name == null || name.isEmpty()) continue;
            if (!keyword.isEmpty() && !name.toLowerCase().contains(keyword.toLowerCase())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("wid", wh.getWid());
            m.put("name", name);
            list.add(m);
        }
        return Result.ok(list);
    }

    /**
     * 根据 SKU 和仓库 wid 匹配运营数据，返回近30天利润、销量和产品等级，用于自动填充备注。
     */
    @GetMapping("/product-info")
    public Result<Map<String, Object>> productInfo(@RequestParam String sku, @RequestParam Integer wid) {
        // 提取 baseSku（前3段，如 RNG-80210-0557 → RNG-80210）
        String[] parts = sku.trim().split("-");
        String baseSku = parts.length >= 2 ? parts[0] + "-" + parts[1] : sku.trim();

        // wid → 站点标签（注意：warehouse 表主键是 id，需要用 wid 字段查）
        WarehouseEntity wh = warehouseService.getByWid(wid);
        String siteLabel = wh != null ? whNameToSite(wh.getName()) : "";

        // 匹配运营数据
        InventoryOverviewItem matched = null;
        if (!siteLabel.isEmpty()) {
            for (InventoryOverviewItem item : overviewService.buildOverview()) {
                if (baseSku.equals(item.getSku()) && siteLabel.equals(item.getWarehouseNames())) {
                    matched = item;
                    break;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (matched != null) {
            BigDecimal profitRate = matched.getLast30DaysProfit();
            int sales = matched.getLast30DaysSales();
            String level = calcProductLevel(sales, profitRate != null ? profitRate.doubleValue() : 0);

            result.put("profitRate", profitRate);
            result.put("sales", sales);
            result.put("level", level);
            result.put("maxReplenish", matched.getMaxMonthlyReplenish());
            result.put("purchaseQuantity", matched.getPurchaseQuantity());
        } else {
            result.put("profitRate", null);
            result.put("sales", 0);
            result.put("level", "—");
            result.put("maxReplenish", null);
            result.put("purchaseQuantity", null);
        }
        return Result.ok(result);
    }

    /** 根据月销和利润率计算产品等级。 */
    private String calcProductLevel(int sales, double profitRate) {
        if (sales >= 30 && profitRate >= 20) return "S";
        if (sales >= 15 && profitRate >= 20) return "A";
        if (sales >= 10 && profitRate >= 18) return "B";
        if ((sales >= 10 && profitRate >= 10) || (sales >= 5 && sales < 10 && profitRate >= 15)) return "C";
        if ((sales < 5 && profitRate >= 15) || (sales >= 5 && sales < 10 && profitRate >= 10 && profitRate < 15)) return "D";
        return "E";
    }

    /** 仓库名称 → 站点标签，与运营数据中的 warehouseNames 保持一致。 */
    private String whNameToSite(String name) {
        if (name == null || name.isEmpty()) return "";
        if (name.startsWith("CTUAMZ")) return "";
        if (name.contains("-US") || name.contains("加州") || name.contains("新泽西")) return "美国";
        if (name.contains("-DE") || name.contains("德国")) return "德国";
        if (name.contains("-UK") || name.contains("英国")) return "英国";
        return "";
    }
}
