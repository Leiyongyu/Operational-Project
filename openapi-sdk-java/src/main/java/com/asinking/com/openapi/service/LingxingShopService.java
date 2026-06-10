package com.asinking.com.openapi.service;

import com.alibaba.fastjson2.JSON;
import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.entity.EbayShopListEntity;
import com.asinking.com.openapi.service.EbayShopListService;
import com.asinking.com.openapi.sdk.core.Config;
import com.asinking.com.openapi.sdk.core.HttpMethod;
import com.asinking.com.openapi.sdk.core.HttpRequest;
import com.asinking.com.openapi.sdk.core.HttpResponse;
import com.asinking.com.openapi.sdk.okhttp.HttpExecutor;
import com.asinking.com.openapi.sdk.sign.ApiSign;
import com.asinking.com.openapi.service.model.EbayShopSyncResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 领星店铺服务：同步 eBay（platform_code=10003）启用的店铺列表，增量 upsert 写入 ebay_shop_list 表。
 */
@Service
public class LingxingShopService {

    private static final String SHOP_LIST_PATH = "pb/mp/shop/v2/getSellerList";

    private final LingxingProperties properties;
    private final LingxingAuthService authService;
    private final ObjectMapper objectMapper;
    private final EbayShopListService ebayShopListService;

    public LingxingShopService(LingxingProperties properties,
                               LingxingAuthService authService,
                               ObjectMapper objectMapper,
                               EbayShopListService ebayShopListService) {
        this.properties = properties;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.ebayShopListService = ebayShopListService;
    }

    /** 分页拉取启用的 eBay 店铺并增量 upsert 到数据库。 */
    // platform_code=10003 是 eBay，status=1 启用中，is_sync=1 已同步
    @Transactional
    public EbayShopSyncResult getActiveEbayShops(int offset, int length) throws Exception {
        String accessToken = authService.getAccessToken();

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        queryParams.put("access_token", accessToken);
        queryParams.put("app_key", properties.getAppId());

        List<Integer> platformCodes = Arrays.asList(10003);
        Map<String, Object> body = new LinkedHashMap<>();
        // 列表字段签名时传 JSON 字符串，签名后替换回数组，否则领星返回 sign not correct
        body.put("platform_code", JSON.toJSONString(platformCodes));
        body.put("is_sync", 1);
        body.put("status", 1);
        body.put("offset", offset);
        body.put("length", length);

        Map<String, Object> signMap = new LinkedHashMap<>(queryParams);
        signMap.putAll(body);

        String sign = ApiSign.sign(signMap, properties.getAppId());
        queryParams.put("sign", sign);
        body.put("platform_code", platformCodes);

        HttpRequest<Object> request = HttpRequest.builder(Object.class)
                .method(HttpMethod.POST)
                .endpoint(properties.getEndpoint())
                .path(SHOP_LIST_PATH)
                .queryParams(queryParams)
                .json(JSON.toJSONString(body))
                .config(buildConfig())
                .build();

        HttpResponse response = HttpExecutor.create().execute(request);
        Object remote = response.readEntity(Object.class);
        SyncStats stats = upsertEbayShopList(remote);
        return new EbayShopSyncResult(stats.inserted, stats.updated, stats.total, remote);
    }

    /** 构建 HTTP 请求配置。 */
    private Config buildConfig() {
        return new Config()
                .withConnectionTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
    }

    /** 将 API 返回的 eBay 店铺列表增量 upsert 到 ebay_shop_list 表。 */
    private SyncStats upsertEbayShopList(Object remoteResponse) {
        List<Map<String, Object>> shops = extractShopList(remoteResponse);
        if (shops.isEmpty()) {
            return new SyncStats(0, 0, 0);
        }

        List<String> storeIds = shops.stream()
                .map(m -> getString(m, "store_id", "storeId"))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        Map<String, EbayShopListEntity> existingByStoreId = ebayShopListService.lambdaQuery()
                .in(EbayShopListEntity::getStoreId, storeIds)
                .list()
                .stream()
                .collect(Collectors.toMap(EbayShopListEntity::getStoreId, e -> e, (a, b) -> a, HashMap::new));

        int inserted = 0;
        int updated = 0;

        List<EbayShopListEntity> toSave = new ArrayList<>();
        for (Map<String, Object> item : shops) {
            String storeId = getString(item, "store_id", "storeId");
            if (!StringUtils.hasText(storeId)) {
                continue;
            }

            EbayShopListEntity entity = existingByStoreId.get(storeId);
            boolean isNew = false;
            if (entity == null) {
                entity = new EbayShopListEntity();
                entity.setId(uuid32());
                entity.setStoreId(storeId);
                entity.setSid("");
                isNew = true;
            }

            entity.setSid(getStringOrDefault(item, entity.getSid(), "sid"));
            entity.setStoreName(getStringOrDefault(item, entity.getStoreName(), "store_name", "storeName"));
            entity.setPlatformCode(getStringOrDefault(item, entity.getPlatformCode(), "platform_code", "platformCode"));
            entity.setPlatformName(getStringOrDefault(item, entity.getPlatformName(), "platform_name", "platformName"));
            entity.setCurrency(getStringOrDefault(item, entity.getCurrency(), "currency"));
            entity.setIsSync(getIntOrDefault(item, entity.getIsSync(), "is_sync", "isSync"));
            entity.setStatus(getIntOrDefault(item, entity.getStatus(), "status"));
            entity.setCountryCode(getStringOrDefault(item, entity.getCountryCode(), "country_code", "countryCode"));

            if (isNew) {
                inserted++;
            } else {
                updated++;
            }
            toSave.add(entity);
        }

        ebayShopListService.saveOrUpdateBatch(toSave);
        return new SyncStats(inserted, updated, toSave.size());
    }

    /** 从 API 响应中提取店铺列表。 */
    private List<Map<String, Object>> extractShopList(Object remoteResponse) {
        if (remoteResponse == null) {
            return Collections.emptyList();
        }
        Map<String, Object> root = objectMapper.convertValue(remoteResponse, new TypeReference<Map<String, Object>>() {
        });
        Object dataObj = root.get("data");
        if (dataObj == null) {
            return Collections.emptyList();
        }
        Map<String, Object> data = objectMapper.convertValue(dataObj, new TypeReference<Map<String, Object>>() {
        });
        Object listObj = data.get("list");
        if (!(listObj instanceof Collection)) {
            return Collections.emptyList();
        }
        Collection<?> rawList = (Collection<?>) listObj;
        return rawList.stream()
                .map(item -> objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {
                }))
                .collect(Collectors.toList());
    }

    /** 按多个 key 依次取值，返回第一个非空文本。 */
    private String getString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val == null) {
                continue;
            }
            String text = String.valueOf(val);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    /** 按多个 key 取值，取不到则返回默认值。 */
    private String getStringOrDefault(Map<String, Object> map, String defaultValue, String... keys) {
        String value = getString(map, keys);
        return StringUtils.hasText(value) ? value : (defaultValue == null ? "" : defaultValue);
    }

    /** 按多个 key 取整数值，取不到则返回默认值。 */
    private Integer getIntOrDefault(Map<String, Object> map, Integer defaultValue, String... keys) {
        String value = getString(map, keys);
        if (!StringUtils.hasText(value)) {
            return defaultValue == null ? 0 : defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue == null ? 0 : defaultValue;
        }
    }

    /** 生成 32 位 UUID（去掉横线）。 */
    private String uuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static final class SyncStats {
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
