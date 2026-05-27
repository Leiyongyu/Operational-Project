package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.response.PurchasePlanCreateResponse;
import com.asinking.com.openapi.entity.WarehouseEntity;
import com.asinking.com.openapi.mapper.mp.EbayProductListingMapper;
import com.asinking.com.openapi.mapper.mp.EbayShopListMapper;
import com.asinking.com.openapi.service.LingxingPurchasePlanService;
import com.asinking.com.openapi.service.WarehouseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/purchase-plan")
public class PurchasePlanController {

    private final LingxingPurchasePlanService service;
    private final EbayProductListingMapper ebayProductMapper;
    private final EbayShopListMapper ebayShopMapper;
    private final WarehouseService warehouseService;

    public PurchasePlanController(LingxingPurchasePlanService service,
                                  EbayProductListingMapper ebayProductMapper,
                                  EbayShopListMapper ebayShopMapper,
                                  WarehouseService warehouseService) {
        this.service = service;
        this.ebayProductMapper = ebayProductMapper;
        this.ebayShopMapper = ebayShopMapper;
        this.warehouseService = warehouseService;
    }

    @PostMapping("/upload")
    public Result<PurchasePlanCreateResponse> upload(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.ok(service.uploadAndCreate(file));
    }

    @PostMapping("/create")
    public Result<PurchasePlanCreateResponse> create(@RequestBody List<Map<String, Object>> data) throws Exception {
        return Result.ok(service.createFromJson(data));
    }

    @GetMapping("/skus")
    public Result<List<Map<String, Object>>> searchSkus(@RequestParam(defaultValue = "") String keyword) {
        Set<String> skus = new LinkedHashSet<>();
        for (var e : ebayProductMapper.selectList(null)) {
            String s = e.getLocalSku();
            if (s == null || s.isEmpty()) continue;
            String[] parts = s.split("-");
            if (parts.length >= 3) s = parts[0] + "-" + parts[1] + "-" + parts[2];
            if (keyword.isEmpty() || s.toLowerCase().contains(keyword.toLowerCase()))
                skus.add(s);
        }
        return Result.ok(skus.stream().limit(500).map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sku", s);
            return m;
        }).collect(Collectors.toList()));
    }

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

    @GetMapping("/warehouses")
    public Result<List<Map<String, Object>>> searchWarehouses(@RequestParam(defaultValue = "") String keyword) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (WarehouseEntity wh : warehouseService.lambdaQuery().list()) {
            String name = wh.getName();
            if (name != null && !name.isEmpty()
                    && (keyword.isEmpty() || name.toLowerCase().contains(keyword.toLowerCase()))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("wid", wh.getWid());
                m.put("name", name);
                list.add(m);
            }
        }
        return Result.ok(list.stream().limit(50).collect(Collectors.toList()));
    }
}
