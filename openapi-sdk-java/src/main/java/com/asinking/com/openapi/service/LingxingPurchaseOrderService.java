package com.asinking.com.openapi.service;

import com.alibaba.fastjson.JSON;
import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.dto.response.SaleStatSyncResponse;
import com.asinking.com.openapi.entity.PurchaseOrderEntity;
import com.asinking.com.openapi.mapper.mp.PurchaseOrderMapper;
import com.asinking.com.openapi.sdk.core.*;
import com.asinking.com.openapi.sdk.okhttp.HttpExecutor;
import com.asinking.com.openapi.sdk.sign.ApiSign;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 领星采购订单服务：按日期范围分页拉取采购订单并增量 upsert 入库。
 */
@Service
public class LingxingPurchaseOrderService {
    private static final String PATH = "erp/sc/routing/data/local_inventory/purchaseOrderList";
    private final LingxingProperties properties;
    private final LingxingAuthService authService;
    private final ObjectMapper objectMapper;
    private final PurchaseOrderMapper mapper;

    /** 构造采购订单服务，注入配置、认证、JSON 工具及 Mapper */
    public LingxingPurchaseOrderService(LingxingProperties properties, LingxingAuthService authService,
                                        ObjectMapper objectMapper, PurchaseOrderMapper mapper) {
        this.properties = properties; this.authService = authService;
        this.objectMapper = objectMapper; this.mapper = mapper;
    }

    /** 按日期范围同步采购订单，以 (order_sn, create_time) 唯一键增量 upsert */
    @Transactional
    public SaleStatSyncResponse sync(String startDate, String endDate) throws Exception {
        // 按 (order_sn, create_time) 唯一键增量
        Map<String, PurchaseOrderEntity> existing = new HashMap<>();
        for (PurchaseOrderEntity e : mapper.selectList(null))
            existing.put(e.getOrderSn() + "|" + fmtTime(e.getCreateTime()), e);

        int inserted = 0, updated = 0, offset = 0, length = 500;
        for (int guard = 0; guard < 1000; guard++) {
            Object resp = callApi(startDate, endDate, offset, length);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.convertValue(resp, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> data = (List<Map<String, Object>>) root.get("data");
            if (data == null || data.isEmpty()) break;
            int total = root.get("total") != null ? Integer.parseInt(String.valueOf(root.get("total"))) : 0;

            for (Map<String, Object> order : data) {
                String orderSn = str(order.get("order_sn"));
                LocalDateTime createTime = parseDT(str(order.get("create_time")));
                String key = orderSn + "|" + fmtTime(createTime);
                List<Map<String, Object>> items = (List<Map<String, Object>>) order.get("item_list");
                if (items == null || items.isEmpty()) continue;

                PurchaseOrderEntity e = existing.get(key);
                boolean isNew = (e == null);
                if (isNew) { e = new PurchaseOrderEntity(); e.setId(uuid32()); e.setOrderSn(orderSn); e.setCreateTime(createTime); }
                e.setCustomOrderSn(str(order.get("custom_order_sn")));
                e.setSupplierId(intVal(order.get("supplier_id")));
                e.setSupplierName(str(order.get("supplier_name")));
                e.setOptUid(intVal(order.get("opt_uid")));
                e.setOptRealname(str(order.get("opt_realname")));
                e.setAuditorRealname(str(order.get("auditor_realname")));
                e.setLastRealname(str(order.get("last_realname")));
                e.setOrderTime(parseDT(str(order.get("order_time"))));
                e.setUpdateTime(str(order.get("update_time")));
                e.setStatus(intVal(order.get("status")));
                e.setStatusText(str(order.get("status_text")));
                e.setWid(intVal(order.get("wid")));
                e.setWareHouseName(str(order.get("ware_house_name")));

                Map<String, Object> firstItem = items.get(0);
                e.setItemSku(str(firstItem.get("sku")));
                e.setItemProductName(str(firstItem.get("product_name")));
                e.setItemProductId(intVal(firstItem.get("product_id")));
                e.setItemQuantityReal(intVal(firstItem.get("quantity_real")));
                e.setItemQuantityEntry(intVal(firstItem.get("quantity_entry")));
                e.setItemQuantityReceive(intVal(firstItem.get("quantity_receive")));
                e.setItemPrice(dec(firstItem.get("price")));
                e.setItemAmount(dec(firstItem.get("amount")));

                if (isNew) { mapper.insert(e); existing.put(key, e); inserted++; }
                else { mapper.updateById(e); updated++; }
            }
            offset += length;
            if (total <= 0 || offset >= total) break;
        }
        return new SaleStatSyncResponse(inserted + updated, inserted + updated, Collections.emptyList());
    }

    private Object callApi(String startDate, String endDate, int offset, int length) throws Exception {
        Map<String, Object> qp = new LinkedHashMap<>();
        qp.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        qp.put("access_token", authService.getAccessToken());
        qp.put("app_key", properties.getAppId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("search_field_time", "create_time");
        body.put("start_date", startDate); body.put("end_date", endDate);
        body.put("offset", offset); body.put("length", length);
        Map<String, Object> sm = new LinkedHashMap<>(qp); sm.putAll(body);
        qp.put("sign", ApiSign.sign(sm, properties.getAppId()));
        return HttpExecutor.create().execute(HttpRequest.builder(Object.class)
                .method(HttpMethod.POST).endpoint(properties.getEndpoint()).path(PATH)
                .queryParams(qp).json(JSON.toJSONString(body))
                .config(new Config().withConnectionTimeout(properties.getConnectTimeout()).withReadTimeout(300000))
                .build()).readEntity(Object.class);
    }

    private String str(Object v) { return v != null ? String.valueOf(v) : ""; }
    private int intVal(Object v) { if (v == null) return 0; try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; } }
    private java.math.BigDecimal dec(Object v) { if (v == null) return java.math.BigDecimal.ZERO; try { return new java.math.BigDecimal(String.valueOf(v)); } catch (Exception e) { return java.math.BigDecimal.ZERO; } }
    private LocalDateTime parseDT(String s) { if (s == null || s.isEmpty()) return null; try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); } catch (Exception e) { return null; } }
    private String fmtTime(LocalDateTime t) { return t != null ? t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : ""; }
    private String uuid32() { return UUID.randomUUID().toString().replace("-", ""); }
}
