package com.asinking.com.openapi.service;

import com.alibaba.fastjson.JSON;
import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.entity.EbayProductListingEntity;
import com.asinking.com.openapi.service.EbayProductListingService;
import com.asinking.com.openapi.sdk.core.Config;
import com.asinking.com.openapi.sdk.core.HttpMethod;
import com.asinking.com.openapi.sdk.core.HttpRequest;
import com.asinking.com.openapi.sdk.core.HttpResponse;
import com.asinking.com.openapi.sdk.okhttp.HttpExecutor;
import com.asinking.com.openapi.sdk.sign.ApiSign;
import com.asinking.com.openapi.service.model.EbayListRequest;
import com.asinking.com.openapi.service.model.EbayProductFullSyncResult;
import com.asinking.com.openapi.service.model.EbayProductSyncResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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
 * 领星 eBay 服务：同步 eBay 商品 listing，增量 upsert 写入 ebay_product_listing 表。
 */
@Service
public class LingxingEbayService {

    private static final String EBAY_LIST_PATH = "basicOpen/multiplatform/ebay/list";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LingxingProperties properties;
    private final LingxingAuthService authService;
    private final ObjectMapper objectMapper;
    private final EbayProductListingService productListingService;
    private final EbayProductDedupService dedupService;

    /** 构造 eBay 服务，注入配置、认证、JSON 工具及 Listing 持久化服务 */
    public LingxingEbayService(LingxingProperties properties,
                               LingxingAuthService authService,
                               ObjectMapper objectMapper,
                               EbayProductListingService productListingService,
                               EbayProductDedupService dedupService) {
        this.properties = properties;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.productListingService = productListingService;
        this.dedupService = dedupService;
    }

    /** 按请求参数拉取单页 eBay 商品 listing，增量 upsert 入库 */
    @Transactional
    public EbayProductSyncResult listEbayItems(EbayListRequest req) throws Exception {
        int offset = req != null && req.getOffset() != null ? req.getOffset() : 0;
        int length = req != null && req.getLength() != null ? req.getLength() : 20;
        length = normalizePageSize(length, 20);

        Object remote = requestEbayList(req, offset, length);
        SyncStats stats = upsertProductListing(remote);
        return new EbayProductSyncResult(stats.inserted, stats.updated, stats.total, remote);
    }

    /** 全量同步 eBay 商品 listing，自动分页遍历直到无更多数据 */
    @Transactional
    public EbayProductFullSyncResult syncAllEbayItems(EbayListRequest req) throws Exception {
        int pageSize = normalizePageSize(req != null && req.getLength() != null ? req.getLength() : 200, 200);
        int offset = 0;
        int pages = 0;
        int inserted = 0;
        int updated = 0;
        int processed = 0;
        int remoteTotal = 0;

        for (int guard = 0; guard < 10000; guard++) {
            Object remote = requestEbayList(req, offset, pageSize);
            remoteTotal = Math.max(remoteTotal, extractRemoteTotal(remote));
            SyncStats stats = upsertProductListing(remote);
            inserted += stats.inserted;
            updated += stats.updated;
            processed += stats.total;
            pages++;

            List<Map<String, Object>> items = extractItemList(remote);
            if (items.isEmpty()) {
                break;
            }

            offset += pageSize;
            if (remoteTotal > 0 && offset >= remoteTotal) {
                break;
            }

            if (items.size() < pageSize) {
                break;
            }
        }

        // 全量同步后自动重建去重表
        int dedupCount = dedupService.rebuildFromListing();
        return new EbayProductFullSyncResult(inserted, updated, processed, pages, remoteTotal, dedupCount);
    }

    private void putScalar(Map<String, Object> bodySend, Map<String, Object> bodySign, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && !StringUtils.hasText((String) value)) {
            return;
        }
        bodySend.put(key, value);
        bodySign.put(key, value);
    }

    private void putList(Map<String, Object> bodySend, Map<String, Object> bodySign, String key, List<?> value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        bodySend.put(key, value);
        bodySign.put(key, JSON.toJSONString(value));
    }

    private Config buildConfig() {
        return new Config()
                .withConnectionTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
    }

    private Object requestEbayList(EbayListRequest req, int offset, int length) throws Exception {
        String accessToken = authService.getAccessToken();

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        queryParams.put("access_token", accessToken);
        queryParams.put("app_key", properties.getAppId());

        Map<String, Object> bodySend = new LinkedHashMap<>();
        Map<String, Object> bodySign = new LinkedHashMap<>();

        putScalar(bodySend, bodySign, "offset", offset);
        putScalar(bodySend, bodySign, "length", length);

        if (req != null) {
            putList(bodySend, bodySign, "store_ids", req.getStoreIds());
            putList(bodySend, bodySign, "site_code", req.getSiteCode());
            putList(bodySend, bodySign, "listing_status", req.getListingStatus());
            putList(bodySend, bodySign, "auto_restocks", req.getAutoRestocks());
            putList(bodySend, bodySign, "listing_type", req.getListingType());

            putScalar(bodySend, bodySign, "search_field", req.getSearchField());
            putScalar(bodySend, bodySign, "search_single_value", req.getSearchSingleValue());
            putScalar(bodySend, bodySign, "listing_time_field", req.getListingTimeField());
            putScalar(bodySend, bodySign, "listing_start_time", req.getListingStartTime());
            putScalar(bodySend, bodySign, "listing_end_time", req.getListingEndTime());
        }

        Map<String, Object> signMap = new LinkedHashMap<>(queryParams);
        signMap.putAll(bodySign);

        String sign = ApiSign.sign(signMap, properties.getAppId());
        queryParams.put("sign", sign);

        HttpRequest<Object> request = HttpRequest.builder(Object.class)
                .method(HttpMethod.POST)
                .endpoint(properties.getEndpoint())
                .path(EBAY_LIST_PATH)
                .queryParams(queryParams)
                .json(JSON.toJSONString(bodySend))
                .config(buildConfig())
                .build();

        HttpResponse response = HttpExecutor.create().execute(request);
        return response.readEntity(Object.class);
    }

    private int extractRemoteTotal(Object remoteResponse) {
        if (remoteResponse == null) {
            return 0;
        }
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

    private int normalizePageSize(int length, int defaultValue) {
        int size = length;
        if (size <= 0) {
            size = defaultValue;
        }
        if (size > 200) {
            size = 200;
        }
        return size;
    }

    private SyncStats upsertProductListing(Object remoteResponse) {
        List<Map<String, Object>> items = extractItemList(remoteResponse);
        if (items.isEmpty()) {
            return new SyncStats(0, 0, 0);
        }

        List<String> itemIds = items.stream()
                .map(m -> getString(m, "item_id", "itemId"))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        Map<String, EbayProductListingEntity> existingByItemId = productListingService.lambdaQuery()
                .in(EbayProductListingEntity::getItemId, itemIds)
                .list()
                .stream()
                .collect(Collectors.toMap(EbayProductListingEntity::getItemId, e -> e, (a, b) -> a, HashMap::new));

        int inserted = 0;
        int updated = 0;
        Map<String, EbayProductListingEntity> toSaveByItemId = new LinkedHashMap<>();
        Set<String> countedItemIds = new HashSet<>();

        for (Map<String, Object> item : items) {
            String itemId = getString(item, "item_id", "itemId");
            if (!StringUtils.hasText(itemId)) {
                continue;
            }

            EbayProductListingEntity entity = existingByItemId.get(itemId);
            boolean isNew = false;
            if (entity == null) {
                entity = new EbayProductListingEntity();
                entity.setId(uuid32());
                entity.setPlatform("eBay");
                entity.setItemId(itemId);
                isNew = true;
                existingByItemId.put(itemId, entity);
            }

            entity.setItemUrl(getStringOrDefault(item, entity.getItemUrl(), "item_url", "itemUrl"));
            entity.setPictureUrl(getString(item, "picture_url", "pictureUrl"));

            entity.setMsku(getStringOrDefaultAllowEmpty(item, "", "msku"));
            String mskuVal = entity.getMsku();
            entity.setSku(extractBaseSku(mskuVal != null ? mskuVal : ""));
            entity.setLocalSku(getStringOrDefaultAllowEmpty(item, "", "local_sku", "localSku"));

            entity.setTitle(getStringOrDefault(item, entity.getTitle(), "title"));
            entity.setLocalName(getString(item, "local_name", "localName"));
            entity.setAttribute(getString(item, "attribute"));

            entity.setListingType(getIntOrDefault(item, entity.getListingType(), "listing_type", "listingType"));
            entity.setListingTypeName(getStringOrDefault(item, entity.getListingTypeName(), "listing_type_name", "listingTypeName"));

            entity.setListingStatus(getIntOrDefault(item, entity.getListingStatus(), "listing_status", "listingStatus"));
            entity.setListingStatusName(getStringOrDefault(item, entity.getListingStatusName(), "listing_status_name", "listingStatusName"));

            entity.setPrice(getDecimalOrDefault(item, entity.getPrice(), "price"));
            entity.setStartPrice(getDecimalOrDefault(item, entity.getStartPrice(), "start_price", "startPrice"));
            entity.setAcceptPrice(getDecimalOrDefault(item, entity.getAcceptPrice(), "accept_price", "acceptPrice"));

            entity.setQuantity(getIntOrDefault(item, entity.getQuantity(), "quantity"));
            entity.setAutoRestock(getIntOrDefault(item, entity.getAutoRestock(), "auto_restock", "autoRestock"));

            Object autoRestockResp = item.get("product_auto_restock_response");
            if (autoRestockResp != null) {
                entity.setProductAutoRestockResponse(JSON.toJSONString(autoRestockResp));
            }

            entity.setLocation(getString(item, "location"));
            entity.setDispatchTimeMax(getIntOrDefault(item, entity.getDispatchTimeMax(), "dispatch_time_max", "dispatchTimeMax"));

            LocalDateTime startTime = parseDateTime(getString(item, "listing_start_time", "listingStartTime"));
            if (startTime != null) {
                entity.setListingStartTime(startTime);
            } else if (entity.getListingStartTime() == null) {
                entity.setListingStartTime(LocalDateTime.now());
            }

            LocalDateTime endTime = parseDateTime(getString(item, "listing_end_time", "listingEndTime"));
            if (endTime != null) {
                entity.setListingEndTime(endTime);
            } else if (entity.getListingEndTime() == null) {
                entity.setListingEndTime(LocalDateTime.now());
            }

            entity.setStoreId(getStringOrDefault(item, entity.getStoreId(), "store_id", "storeId"));
            entity.setStoreName(getStringOrDefault(item, entity.getStoreName(), "store_name", "storeName"));
            entity.setSiteCode(getStringOrDefault(item, entity.getSiteCode(), "site_code", "siteCode"));
            entity.setSiteName(getStringOrDefault(item, entity.getSiteName(), "site_name", "siteName"));

            toSaveByItemId.put(itemId, entity);
            if (countedItemIds.add(itemId)) {
                if (isNew) {
                    inserted++;
                } else {
                    updated++;
                }
            }
        }

        List<EbayProductListingEntity> toSave = new ArrayList<>(toSaveByItemId.values());
        productListingService.saveOrUpdateBatch(toSave);
        return new SyncStats(inserted, updated, toSave.size());
    }

    private List<Map<String, Object>> extractItemList(Object remoteResponse) {
        if (remoteResponse == null) {
            return Collections.emptyList();
        }
        Map<String, Object> root = objectMapper.convertValue(remoteResponse, new TypeReference<Map<String, Object>>() {
        });
        Object dataObj = root.get("data");
        if (!(dataObj instanceof Collection)) {
            return Collections.emptyList();
        }
        Collection<?> rawList = (Collection<?>) dataObj;
        return rawList.stream()
                .map(item -> objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {
                }))
                .collect(Collectors.toList());
    }

    private String getString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val == null) {
                continue;
            }
            String text = String.valueOf(val);
            if (StringUtils.hasText(text) && !"null".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return null;
    }

    private String getStringOrDefault(Map<String, Object> map, String defaultValue, String... keys) {
        String value = getString(map, keys);
        return StringUtils.hasText(value) ? value : (defaultValue == null ? "" : defaultValue);
    }

    private String getStringOrDefaultAllowEmpty(Map<String, Object> map, String defaultValue, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val == null) {
                continue;
            }
            String text = String.valueOf(val);
            if (!"null".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return defaultValue == null ? "" : defaultValue;
    }

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

    private BigDecimal getDecimalOrDefault(Map<String, Object> map, BigDecimal defaultValue, String... keys) {
        String value = getString(map, keys);
        if (!StringUtils.hasText(value)) {
            return defaultValue == null ? BigDecimal.ZERO : defaultValue;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            return defaultValue == null ? BigDecimal.ZERO : defaultValue;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractBaseSku(String msku) {
        if (msku == null || msku.isEmpty()) return "";
        String[] parts = msku.split("-");
        if (parts.length < 2) return msku;
        // 首段是 "数字PC" 格式（如 2PC、4PC）时保留前三段
        if (parts[0].matches("\\d+PC")) {
            if (parts.length >= 3) return parts[0] + "-" + parts[1] + "-" + parts[2];
            return parts[0] + "-" + parts[1];
        }
        // 正常情况保留前两段
        return parts[0] + "-" + parts[1];
    }

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
