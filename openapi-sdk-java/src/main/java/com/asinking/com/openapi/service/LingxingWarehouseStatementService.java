package com.asinking.com.openapi.service;

import com.alibaba.fastjson.JSON;
import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.dto.response.SaleStatSyncResponse;
import com.asinking.com.openapi.entity.WarehouseStatementEntity;
import com.asinking.com.openapi.mapper.mp.WarehouseStatementMapper;
import com.asinking.com.openapi.sdk.core.*;
import com.asinking.com.openapi.sdk.okhttp.HttpExecutor;
import com.asinking.com.openapi.sdk.sign.ApiSign;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 仓库库存流水同步服务，增量 upsert 按 statement_id 唯一键。
 */
@Service
public class LingxingWarehouseStatementService {

    private static final String PATH = "erp/sc/routing/inventoryLog/WareHouseInventory/wareHouseCenterStatement";
    private static final String WIDS = "18676,18701,18675,18674,18702,18700,18699";

    private final LingxingProperties properties;
    private final LingxingAuthService authService;
    private final ObjectMapper objectMapper;
    private final WarehouseStatementMapper mapper;

    public LingxingWarehouseStatementService(LingxingProperties properties,
                                             LingxingAuthService authService,
                                             ObjectMapper objectMapper,
                                             WarehouseStatementMapper mapper) {
        this.properties = properties;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    /** 按时间范围分页拉取仓库库存流水，按 (wid, sku, opt_time) 唯一键增量 upsert。 */
    @Transactional
    public SaleStatSyncResponse syncStatements(String startDate, String endDate) throws Exception {
        // 按 (wid, sku, opt_time) 唯一键增量 upsert
        Map<String, WarehouseStatementEntity> existing = new HashMap<>();
        for (WarehouseStatementEntity e : mapper.selectList(null))
            existing.put(e.getWid() + "|" + e.getSku() + "|" + fmtTime(e.getOptTime()), e);

        int inserted = 0, updated = 0;
        int offset = 0, length = 200;

        for (int guard = 0; guard < 1000; guard++) {
            Object resp = callApi(startDate, endDate, offset, length);
            Map<String, Object> root = objectMapper.convertValue(resp, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> data = (List<Map<String, Object>>) root.get("data");
            if (data == null || data.isEmpty()) break;
            int total = root.get("total") != null ? Integer.parseInt(String.valueOf(root.get("total"))) : 0;

            for (Map<String, Object> item : data) {
                Integer wid = intVal(item.get("wid"));
                String sku = str(item.get("sku"));
                LocalDateTime optTime = parseDateTime(str(item.get("opt_time")));
                String key = wid + "|" + sku + "|" + fmtTime(optTime);
                WarehouseStatementEntity e = existing.get(key);
                boolean isNew = (e == null);
                if (isNew) {
                    e = new WarehouseStatementEntity();
                    e.setId(uuid32());
                    e.setStatementId(str(item.get("statement_id")));
                    e.setWid(wid);
                    e.setSku(sku);
                    e.setOptTime(optTime);
                }
                e.setWareHouseName(str(item.get("ware_house_name")));
                e.setOrderSn(str(item.get("order_sn")));
                e.setRefOrderSn(str(item.get("ref_order_sn")));
                e.setSellerId(str(item.get("seller_id")));
                e.setFnsku(str(item.get("fnsku")));
                e.setType(intVal(item.get("type")));
                e.setTypeText(str(item.get("type_text")));
                e.setSubType(str(item.get("sub_type")));
                e.setSubTypeText(str(item.get("sub_type_text")));
                e.setProductName(str(item.get("product_name")));
                e.setProductGoodNum(intVal(item.get("product_good_num")));
                e.setProductBadNum(intVal(item.get("product_bad_num")));
                if (isNew) { mapper.insert(e); existing.put(key, e); inserted++; }
                else { mapper.updateById(e); updated++; }
            }
            offset += length;
            if (total <= 0 || offset >= total) break;
        }
        return new SaleStatSyncResponse(inserted + updated, inserted + updated, Collections.emptyList());
    }

    /** 调用领星仓库库存流水 API。 */
    private Object callApi(String startDate, String endDate, int offset, int length) throws Exception {
        Map<String, Object> qp = new LinkedHashMap<>();
        qp.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        qp.put("access_token", authService.getAccessToken());
        qp.put("app_key", properties.getAppId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wids", WIDS);
        body.put("types", "22");
        body.put("start_date", startDate);
        body.put("end_date", endDate);
        body.put("offset", offset);
        body.put("length", length);

        Map<String, Object> signMap = new LinkedHashMap<>(qp);
        signMap.putAll(body);
        qp.put("sign", ApiSign.sign(signMap, properties.getAppId()));

        return HttpExecutor.create().execute(HttpRequest.builder(Object.class)
                .method(HttpMethod.POST).endpoint(properties.getEndpoint()).path(PATH)
                .queryParams(qp).json(JSON.toJSONString(body))
                .config(new Config().withConnectionTimeout(properties.getConnectTimeout()).withReadTimeout(300000))
                .build()).readEntity(Object.class);
    }

    /** 安全转换为字符串，null 返回空串。 */
    private String str(Object v) { return v != null ? String.valueOf(v) : ""; }
    /** 安全转换为 int，null 或异常返回 0。 */
    private int intVal(Object v) {
        if (v == null) return 0;
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
    /** 解析日期时间字符串，支持 "yyyy-MM-dd HH:mm" 和 "yyyy-MM-dd HH:mm:ss" 两种格式。 */
    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return s.length() == 16 ? LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                      : LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception e) { return null; }
    }
    /** 格式化 LocalDateTime 为 "yyyy-MM-dd HH:mm"。 */
    private String fmtTime(LocalDateTime t) {
        return t != null ? t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "";
    }
    /** 生成 32 位 UUID（去掉横线）。 */
    private String uuid32() { return UUID.randomUUID().toString().replace("-", ""); }
}
