package com.asinking.com.openapi.service;

import com.alibaba.fastjson2.JSON;
import com.asinking.com.openapi.common.exception.BusinessException;
import com.asinking.com.openapi.common.response.ResultCode;
import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.dto.request.WarehouseInventoryDetailFullSyncRequest;
import com.asinking.com.openapi.dto.request.WarehouseInventoryDetailSyncRequest;
import com.asinking.com.openapi.dto.response.WarehouseInventoryDetailSyncResponse;
import com.asinking.com.openapi.entity.WarehouseInventoryDetailEntity;
import com.asinking.com.openapi.sdk.core.Config;
import com.asinking.com.openapi.sdk.core.HttpMethod;
import com.asinking.com.openapi.sdk.core.HttpRequest;
import com.asinking.com.openapi.sdk.core.HttpResponse;
import com.asinking.com.openapi.sdk.okhttp.HttpExecutor;
import com.asinking.com.openapi.sdk.sign.ApiSign;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 领星仓库库存明细服务：全量同步（先删后插），拍平 API 返回数据写入 warehouse_inventory_detail 表。
 */
@Service
public class LingxingWarehouseInventoryService {

    private static final String INVENTORY_DETAILS_PATH = "erp/sc/routing/data/local_inventory/inventoryDetails";

    private final LingxingProperties properties;
    private final LingxingAuthService authService;
    private final ObjectMapper objectMapper;
    private final WarehouseInventoryDetailService warehouseInventoryDetailService;
    private final LingxingWarehouseService lingxingWarehouseService;

    public LingxingWarehouseInventoryService(LingxingProperties properties,
                                            LingxingAuthService authService,
                                            ObjectMapper objectMapper,
                                            WarehouseInventoryDetailService warehouseInventoryDetailService,
                                            LingxingWarehouseService lingxingWarehouseService) {
        this.properties = properties;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.warehouseInventoryDetailService = warehouseInventoryDetailService;
        this.lingxingWarehouseService = lingxingWarehouseService;
    }

    /** 按仓库 wid 同步库存明细（增量 upsert）。 */
    @Transactional
    public WarehouseInventoryDetailSyncResponse syncInventoryDetails(WarehouseInventoryDetailSyncRequest req) throws Exception {
        if (req == null || !StringUtils.hasText(req.getWid())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "wid 不能为空");
        }
        Object remote = callInventoryDetails(req.getWid(), req.getOffset(), req.getLength(), req.getSku());

        SyncStats stats = upsertInventoryDetails(remote);
        return new WarehouseInventoryDetailSyncResponse(stats.inserted, stats.updated, stats.total, remote);
    }

    /** 按仓库 wid 拉取库存明细原始数据（不落库）。 */
    public Object fetchInventoryDetails(WarehouseInventoryDetailSyncRequest req) throws Exception {
        if (req == null || !StringUtils.hasText(req.getWid())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "wid 不能为空");
        }
        return callInventoryDetails(req.getWid(), req.getOffset(), req.getLength(), req.getSku());
    }

    /** 拉取所有海外仓的库存明细首页数据（含仓库和库存原始数据）。 */
    public Object fetchInventoryDetailsFirstPageFromOverseaWarehouses(WarehouseInventoryDetailFullSyncRequest req) throws Exception {
        int length = req != null && req.getLength() != null ? Math.min(Math.max(req.getLength(), 1), 800) : 20;
        String sku = req != null && StringUtils.hasText(req.getSku()) ? req.getSku().trim() : null;

        Object warehouseRemote = lingxingWarehouseService.fetchOverseaWarehouses(null);
        List<Integer> wids = lingxingWarehouseService.extractOverseaWarehouseWids(warehouseRemote);
        if (wids.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未获取到海外仓 wid 列表");
        }
        String widStr = wids.stream().map(String::valueOf).collect(Collectors.joining(","));

        Object inventoryRemote = callInventoryDetails(widStr, 0, length, sku);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("wids", wids);
        resp.put("warehouses", warehouseRemote);
        resp.put("inventoryDetails", inventoryRemote);
        return resp;
    }

    /** 全量同步库存明细：清空表后按配置的仓库分页拉取并批量插入。 */
    @Transactional
    public WarehouseInventoryDetailSyncResponse syncAllInventoryDetails(WarehouseInventoryDetailFullSyncRequest req) throws Exception {
        int length = req != null && req.getLength() != null ? Math.min(Math.max(req.getLength(), 1), 800) : 200;
        String sku = req != null && StringUtils.hasText(req.getSku()) ? req.getSku().trim() : null;

        // 从配置读取需要同步库存的仓库 wid 列表
        List<Integer> wids = parseInventoryWids();
        if (wids.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未配置 inventory-wids，请在 application.yml 中配置需要同步库存的仓库");
        }
        String widStr = wids.stream().map(String::valueOf).collect(Collectors.joining(","));

        // 全量同步：先删除所有库存明细
        warehouseInventoryDetailService.lambdaUpdate().remove();

        int offset = 0;
        int inserted = 0;
        int total = 0;
        Object lastRemote = null;

        while (true) {
            Object remote = callInventoryDetails(widStr, offset, length, sku);
            List<WarehouseInventoryDetailEntity> entities = parseInventoryPage(remote);
            if (!entities.isEmpty()) {
                warehouseInventoryDetailService.saveBatch(entities);
                inserted += entities.size();
            }

            total = extractTotal(remote);
            lastRemote = remote;

            offset += length;
            if (total <= 0 || offset >= total) {
                break;
            }
        }

        return new WarehouseInventoryDetailSyncResponse(inserted, 0, total, lastRemote);
    }

    /** 解析 API 返回的一页库存数据为实体列表（按 wid+productId 去重）。 */
    private List<WarehouseInventoryDetailEntity> parseInventoryPage(Object remoteResponse) {
        Map<String, Object> root = objectMapper.convertValue(remoteResponse, new TypeReference<Map<String, Object>>() {
        });
        List<Map<String, Object>> data = getList(root, "data");
        if (data.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> seen = new HashSet<>();
        List<WarehouseInventoryDetailEntity> entities = new ArrayList<>(data.size());

        for (Map<String, Object> row : data) {
            Integer wid = getIntObj(row, "wid");
            Integer productId = getIntObj(row, "product_id", "productId");
            if (wid == null || productId == null) {
                continue;
            }
            String key = key(wid, productId);
            if (!seen.add(key)) {
                continue;
            }

            WarehouseInventoryDetailEntity entity = new WarehouseInventoryDetailEntity();
            entity.setId(uuid32());
            entity.setWid(wid);
            entity.setProductId(productId);
            entity.setSku(getString(row, "sku"));
            entity.setSellerId(getString(row, "seller_id", "sellerId"));
            entity.setFnsku(getString(row, "fnsku"));
            entity.setProductTotal(getIntObj(row, "product_total", "productTotal"));
            entity.setProductValidNum(getIntObj(row, "product_valid_num", "productValidNum"));
            entity.setProductBadNum(getIntObj(row, "product_bad_num", "productBadNum"));
            entity.setProductQcNum(getIntObj(row, "product_qc_num", "productQcNum"));
            entity.setProductLockNum(getIntObj(row, "product_lock_num", "productLockNum"));
            entity.setGoodLockNum(getIntObj(row, "good_lock_num", "goodLockNum"));
            entity.setBadLockNum(getIntObj(row, "bad_lock_num", "badLockNum"));
            entity.setStockCostTotal(getDecimal(row, "stock_cost_total", "stockCostTotal"));
            entity.setQuantityReceive(getDecimal(row, "quantity_receive", "quantityReceive"));
            entity.setStockCost(getDecimal(row, "stock_cost", "stockCost"));
            entity.setProductOnway(getIntObj(row, "product_onway", "productOnway"));
            entity.setTransitHeadCost(getDecimal(row, "transit_head_cost", "transitHeadCost"));
            entity.setAverageAge(getIntObj(row, "average_age", "averageAge"));
            entity.setExpectValidNum(getIntObj(row, "expect_valid_num", "expectValidNum"));
            entity.setExpectPendingNum(getDecimal(row, "expect_pending_num", "expectPendingNum"));
            entity.setAvailableInventoryBoxQty(getIntObj(row, "available_inventory_box_qty", "availableInventoryBoxQty"));
            entity.setPurchasePrice(getDecimal(row, "purchase_price", "purchasePrice"));
            entity.setPrice(getDecimal(row, "price"));
            entity.setHeadStockPrice(getDecimal(row, "head_stock_price", "headStockPrice"));
            entity.setStockPrice(getDecimal(row, "stock_price", "stockPrice"));
            entity.setThirdInventoryJson(getJson(row, "third_inventory", "thirdInventory"));
            entity.setStockAgeListJson(getJson(row, "stock_age_list", "stockAgeList"));
            entities.add(entity);
        }

        return entities;
    }

    /**
     * 从配置 lingxing.inventory-wids 解析需要同步库存的仓库 WID 列表。
     */
    private List<Integer> parseInventoryWids() {
        String raw = properties.getInventoryWids();
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    /** 从 API 响应中提取 total 字段。 */
    private int extractTotal(Object remoteResponse) {
        Map<String, Object> root = objectMapper.convertValue(remoteResponse, new TypeReference<Map<String, Object>>() {
        });
        Object totalObj = root.get("total");
        if (totalObj == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(totalObj));
        } catch (Exception e) {
            return 0;
        }
    }

    /** 调用领星库存明细 API。 */
    private Object callInventoryDetails(String widStr, Integer offset, Integer length, String sku) throws Exception {
        int o = offset == null ? 0 : Math.max(offset, 0);
        int l = length == null ? 20 : Math.min(Math.max(length, 1), 800);

        String accessToken = authService.getAccessToken();

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        queryParams.put("access_token", accessToken);
        queryParams.put("app_key", properties.getAppId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wid", widStr);
        body.put("offset", o);
        body.put("length", l);
        if (StringUtils.hasText(sku)) {
            body.put("sku", sku.trim());
        }

        Map<String, Object> signMap = new LinkedHashMap<>(queryParams);
        signMap.putAll(body);
        String sign = ApiSign.sign(signMap, properties.getAppId());
        queryParams.put("sign", sign);

        HttpRequest<Object> request = HttpRequest.builder(Object.class)
                .method(HttpMethod.POST)
                .endpoint(properties.getEndpoint())
                .path(INVENTORY_DETAILS_PATH)
                .queryParams(queryParams)
                .json(JSON.toJSONString(body))
                .config(buildConfig())
                .build();

        HttpResponse response = HttpExecutor.create().execute(request);
        return response.readEntity(Object.class);
    }

    /** 构建 HTTP 请求配置。 */
    private Config buildConfig() {
        return new Config()
                .withConnectionTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
    }

    /** 将 API 返回的库存明细增量 upsert 到 warehouse_inventory_detail 表。 */
    private SyncStats upsertInventoryDetails(Object remoteResponse) throws Exception {
        Map<String, Object> root = objectMapper.convertValue(remoteResponse, new TypeReference<Map<String, Object>>() {
        });
        int total = getInt(root, "total");
        List<Map<String, Object>> data = getList(root, "data");
        if (data.isEmpty()) {
            return new SyncStats(0, 0, total);
        }

        List<Key> keys = new ArrayList<>(data.size());
        for (Map<String, Object> row : data) {
            Integer wid = getIntObj(row, "wid");
            Integer productId = getIntObj(row, "product_id", "productId");
            if (wid == null || productId == null) {
                continue;
            }
            keys.add(new Key(wid, productId));
        }
        if (keys.isEmpty()) {
            return new SyncStats(0, 0, total);
        }

        List<Integer> widList = keys.stream().map(k -> k.wid).distinct().collect(Collectors.toList());
        List<Integer> productIdList = keys.stream().map(k -> k.productId).distinct().collect(Collectors.toList());

        Map<String, WarehouseInventoryDetailEntity> existing = warehouseInventoryDetailService.lambdaQuery()
                .in(WarehouseInventoryDetailEntity::getWid, widList)
                .in(WarehouseInventoryDetailEntity::getProductId, productIdList)
                .list()
                .stream()
                .collect(Collectors.toMap(e -> key(e.getWid(), e.getProductId()), e -> e, (a, b) -> a, HashMap::new));

        int inserted = 0;
        int updated = 0;
        List<WarehouseInventoryDetailEntity> toInsert = new ArrayList<>();
        List<WarehouseInventoryDetailEntity> toUpdate = new ArrayList<>();

        for (Map<String, Object> row : data) {
            Integer wid = getIntObj(row, "wid");
            Integer productId = getIntObj(row, "product_id", "productId");
            if (wid == null || productId == null) {
                continue;
            }
            String key = key(wid, productId);
            WarehouseInventoryDetailEntity entity = existing.get(key);
            boolean isNew = false;
            if (entity == null) {
                entity = new WarehouseInventoryDetailEntity();
                entity.setId(uuid32());
                entity.setWid(wid);
                entity.setProductId(productId);
                isNew = true;
            }

            entity.setSku(getString(row, "sku"));
            entity.setSellerId(getString(row, "seller_id", "sellerId"));
            entity.setFnsku(getString(row, "fnsku"));
            entity.setProductTotal(getIntObj(row, "product_total", "productTotal"));
            entity.setProductValidNum(getIntObj(row, "product_valid_num", "productValidNum"));
            entity.setProductBadNum(getIntObj(row, "product_bad_num", "productBadNum"));
            entity.setProductQcNum(getIntObj(row, "product_qc_num", "productQcNum"));
            entity.setProductLockNum(getIntObj(row, "product_lock_num", "productLockNum"));
            entity.setGoodLockNum(getIntObj(row, "good_lock_num", "goodLockNum"));
            entity.setBadLockNum(getIntObj(row, "bad_lock_num", "badLockNum"));
            entity.setStockCostTotal(getDecimal(row, "stock_cost_total", "stockCostTotal"));
            entity.setQuantityReceive(getDecimal(row, "quantity_receive", "quantityReceive"));
            entity.setStockCost(getDecimal(row, "stock_cost", "stockCost"));
            entity.setProductOnway(getIntObj(row, "product_onway", "productOnway"));
            entity.setTransitHeadCost(getDecimal(row, "transit_head_cost", "transitHeadCost"));
            entity.setAverageAge(getIntObj(row, "average_age", "averageAge"));
            entity.setExpectValidNum(getIntObj(row, "expect_valid_num", "expectValidNum"));
            entity.setExpectPendingNum(getDecimal(row, "expect_pending_num", "expectPendingNum"));
            entity.setAvailableInventoryBoxQty(getIntObj(row, "available_inventory_box_qty", "availableInventoryBoxQty"));
            entity.setPurchasePrice(getDecimal(row, "purchase_price", "purchasePrice"));
            entity.setPrice(getDecimal(row, "price"));
            entity.setHeadStockPrice(getDecimal(row, "head_stock_price", "headStockPrice"));
            entity.setStockPrice(getDecimal(row, "stock_price", "stockPrice"));
            entity.setThirdInventoryJson(getJson(row, "third_inventory", "thirdInventory"));
            entity.setStockAgeListJson(getJson(row, "stock_age_list", "stockAgeList"));

            if (isNew) {
                toInsert.add(entity);
                existing.put(key, entity);
                inserted++;
            } else {
                toUpdate.add(entity);
                updated++;
            }
        }

        if (!toInsert.isEmpty()) {
            warehouseInventoryDetailService.saveBatch(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            warehouseInventoryDetailService.updateBatchById(toUpdate);
        }

        return new SyncStats(inserted, updated, total);
    }

    /** 按多个 key 依次取值，返回第一个非空文本。 */
    private String getString(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String k : keys) {
            Object v = map.get(k);
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v);
            if (StringUtils.hasText(s)) {
                return s;
            }
        }
        return null;
    }

    /** 按 key 取 int 值，取不到返回 0。 */
    private int getInt(Map<String, Object> map, String key) {
        Integer v = getIntObj(map, key);
        return v == null ? 0 : v;
    }

    /** 按多个 key 取 Integer 值，取不到返回 null。 */
    private Integer getIntObj(Map<String, Object> map, String... keys) {
        String s = getString(map, keys);
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 按多个 key 取 BigDecimal 值，取不到返回 null。 */
    private BigDecimal getDecimal(Map<String, Object> map, String... keys) {
        String s = getString(map, keys);
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 按多个 key 取值并序列化为 JSON 字符串。 */
    private String getJson(Map<String, Object> map, String... keys) {
        Object v = null;
        for (String k : keys) {
            if (map.containsKey(k)) {
                v = map.get(k);
                break;
            }
        }
        if (v == null) {
            return null;
        }
        return JSON.toJSONString(v);
    }

    /** 从 map 中提取指定 key 的列表。 */
    private List<Map<String, Object>> getList(Map<String, Object> map, String key) {
        if (map == null) {
            return Collections.emptyList();
        }
        Object v = map.get(key);
        if (v == null) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.convertValue(v, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    /** 生成 32 位 UUID（去掉横线）。 */
    private String uuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 生成 wid+productId 复合键。 */
    private String key(Integer wid, Integer productId) {
        return wid + "_" + productId;
    }

    private static class Key {
        private final Integer wid;
        private final Integer productId;

        private Key(Integer wid, Integer productId) {
            this.wid = wid;
            this.productId = productId;
        }
    }

    private static class SyncStats {
        private final int inserted;
        private final int updated;
        private final int total;

        private SyncStats(int inserted, int updated, int total) {
            this.inserted = inserted;
            this.updated = updated;
            this.total = total;
        }
    }
}
