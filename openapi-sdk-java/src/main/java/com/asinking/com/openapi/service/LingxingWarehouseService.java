package com.asinking.com.openapi.service;

import com.alibaba.fastjson.JSON;
import com.asinking.com.openapi.common.exception.BusinessException;
import com.asinking.com.openapi.common.response.ResultCode;
import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.dto.request.WarehouseSyncRequest;
import com.asinking.com.openapi.dto.response.WarehouseSyncResponse;
import com.asinking.com.openapi.entity.WarehouseEntity;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 领星海外仓服务：同步 type=3 海外仓数据，增量 upsert 写入 warehouse 表。
 */
@Service
public class LingxingWarehouseService {

    private static final String WAREHOUSE_PATH = "erp/sc/data/local_inventory/warehouse";

    private final LingxingProperties properties;
    private final LingxingAuthService authService;
    private final ObjectMapper objectMapper;
    private final WarehouseService warehouseService;

    public LingxingWarehouseService(LingxingProperties properties,
                                   LingxingAuthService authService,
                                   ObjectMapper objectMapper,
                                   WarehouseService warehouseService) {
        this.properties = properties;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.warehouseService = warehouseService;
    }

    /** 拉取海外仓（type=3）原始数据，不落库。 */
    // type=1 本地仓, type=3 海外仓, is_delete=0 — 系统设置的仓库列表
    public Object fetchOverseaWarehouses(WarehouseSyncRequest req) throws Exception {
        return callWarehouseApi(3, req);
    }

    /** 同步本地仓和海外仓数据，增量 upsert 到 warehouse 表。 */
    @Transactional
    public WarehouseSyncResponse syncOverseaWarehouses(WarehouseSyncRequest req) throws Exception {
        // 拉取 type=1（本地仓）和 type=3（海外仓），合并后写入
        Object remoteType1 = callWarehouseApi(1, req);
        SyncStats stats1 = upsertWarehouses(remoteType1);

        Object remoteType3 = callWarehouseApi(3, req);
        SyncStats stats3 = upsertWarehouses(remoteType3);

        int inserted = stats1.inserted + stats3.inserted;
        int updated = stats1.updated + stats3.updated;
        int total = stats1.total + stats3.total;
        // 返回 type=3 的数据供前端展示，type=1 的数据在 remoteType1 里
        return new WarehouseSyncResponse(inserted, updated, total, remoteType3);
    }

    /** 调用领星仓库列表 API。 */
    private Object callWarehouseApi(int type, WarehouseSyncRequest req) throws Exception {
        int offset = req != null && req.getOffset() != null ? Math.max(req.getOffset(), 0) : 0;
        int length = req != null && req.getLength() != null ? Math.min(Math.max(req.getLength(), 1), 1000) : 1000;

        String accessToken = authService.getAccessToken();

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        queryParams.put("access_token", accessToken);
        queryParams.put("app_key", properties.getAppId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("is_delete", 0);
        body.put("offset", offset);
        body.put("length", length);

        Map<String, Object> signMap = new LinkedHashMap<>(queryParams);
        signMap.putAll(body);
        String sign = ApiSign.sign(signMap, properties.getAppId());
        queryParams.put("sign", sign);

        HttpRequest<Object> request = HttpRequest.builder(Object.class)
                .method(HttpMethod.POST)
                .endpoint(properties.getEndpoint())
                .path(WAREHOUSE_PATH)
                .queryParams(queryParams)
                .json(JSON.toJSONString(body))
                .config(buildConfig())
                .build();

        HttpResponse response = HttpExecutor.create().execute(request);
        return response.readEntity(Object.class);
    }

    /** 从数据库查出所有海外仓（type=3, is_delete=0）的 wid 列表。 */
    public List<Integer> listOverseaWarehouseWids() {
        return warehouseService.lambdaQuery()
                .eq(WarehouseEntity::getType, 3)
                .eq(WarehouseEntity::getIsDelete, 0)
                .list()
                .stream()
                .map(WarehouseEntity::getWid)
                .filter(w -> w != null)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 从 API 原始响应中提取海外仓（type=3）的 wid 列表。 */
    public List<Integer> extractOverseaWarehouseWids(Object warehouseRemote) {
        Map<String, Object> root = objectMapper.convertValue(warehouseRemote, new TypeReference<Map<String, Object>>() {
        });
        List<Map<String, Object>> data = getList(root, "data");
        if (data.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> wids = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Integer type = getIntObj(row, "type");
            Integer isDelete = getIntObj(row, "is_delete", "isDelete");
            if (type != null && type != 3) {
                continue;
            }
            if (isDelete != null && isDelete != 0) {
                continue;
            }
            Integer wid = getIntObj(row, "wid", "warehouse_id", "warehouseId", "id");
            if (wid != null) {
                wids.add(wid);
            }
        }
        return wids.stream().distinct().collect(Collectors.toList());
    }

    /** 构建 HTTP 请求配置。 */
    private Config buildConfig() {
        return new Config()
                .withConnectionTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
    }

    /** 将 API 返回的仓库数据增量 upsert 到 warehouse 表。 */
    private SyncStats upsertWarehouses(Object remoteResponse) {
        Map<String, Object> root = objectMapper.convertValue(remoteResponse, new TypeReference<Map<String, Object>>() {
        });
        int total = getInt(root, "total");
        List<Map<String, Object>> data = getList(root, "data");
        if (data.isEmpty()) {
            return new SyncStats(0, 0, total);
        }

        List<Integer> wids = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Integer wid = getIntObj(row, "wid", "warehouse_id", "warehouseId", "id");
            if (wid != null) {
                wids.add(wid);
            }
        }
        if (wids.isEmpty()) {
            return new SyncStats(0, 0, total);
        }

        Map<Integer, WarehouseEntity> existing = warehouseService.lambdaQuery()
                .in(WarehouseEntity::getWid, wids.stream().distinct().collect(Collectors.toList()))
                .list()
                .stream()
                .collect(Collectors.toMap(WarehouseEntity::getWid, e -> e, (a, b) -> a, HashMap::new));

        int inserted = 0;
        int updated = 0;
        List<WarehouseEntity> toInsert = new ArrayList<>();
        List<WarehouseEntity> toUpdate = new ArrayList<>();

        for (Map<String, Object> row : data) {
            Integer wid = getIntObj(row, "wid", "warehouse_id", "warehouseId", "id");
            if (wid == null) {
                continue;
            }

            Integer type = getIntObj(row, "type");
            Integer isDelete = getIntObj(row, "is_delete", "isDelete");
            // 存储 type=1（本地仓）和 type=3（海外仓），过滤其他类型和已删除
            if (type != null && type != 1 && type != 3) {
                continue;
            }
            if (isDelete != null && isDelete != 0) {
                continue;
            }

            WarehouseEntity entity = existing.get(wid);
            boolean isNew = false;
            if (entity == null) {
                entity = new WarehouseEntity();
                entity.setId(uuid32());
                entity.setWid(wid);
                existing.put(wid, entity);
                isNew = true;
            }

            entity.setName(getString(row, "name"));
            entity.setType(type != null ? type : 0);
            entity.setSubType(getIntObj(row, "sub_type", "subType"));
            entity.setIsDelete(isDelete != null ? isDelete : 0);
            entity.setCountryCode(getString(row, "country_code", "countryCode"));
            entity.setWpId(getIntObj(row, "wp_id", "wpId"));
            entity.setWpName(getString(row, "wp_name", "wpName"));
            entity.setTWarehouseName(getString(row, "t_warehouse_name", "tWarehouseName"));
            entity.setTWarehouseCode(getString(row, "t_warehouse_code", "tWarehouseCode"));
            entity.setTCountryAreaName(getString(row, "t_country_area_name", "tCountryAreaName"));
            entity.setTStatus(getIntObj(row, "t_status", "tStatus"));
            entity.setRawJson(JSON.toJSONString(row));

            if (isNew) {
                toInsert.add(entity);
                inserted++;
            } else {
                toUpdate.add(entity);
                updated++;
            }
        }

        if (!toInsert.isEmpty()) {
            warehouseService.saveBatch(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            warehouseService.updateBatchById(toUpdate);
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
