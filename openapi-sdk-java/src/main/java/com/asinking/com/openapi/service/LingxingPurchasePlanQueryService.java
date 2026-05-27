package com.asinking.com.openapi.service;

import com.alibaba.fastjson.JSON;
import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.dto.response.SaleStatSyncResponse;
import com.asinking.com.openapi.entity.PurchasePlanEntity;
import com.asinking.com.openapi.mapper.mp.PurchasePlanMapper;
import com.asinking.com.openapi.sdk.core.*;
import com.asinking.com.openapi.sdk.okhttp.HttpExecutor;
import com.asinking.com.openapi.sdk.sign.ApiSign;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class LingxingPurchasePlanQueryService {
    private static final String PATH = "erp/sc/routing/data/local_inventory/getPurchasePlans";

    private final LingxingProperties properties;
    private final LingxingAuthService authService;
    private final ObjectMapper objectMapper;
    private final PurchasePlanMapper mapper;

    public LingxingPurchasePlanQueryService(LingxingProperties properties, LingxingAuthService authService,
                                            ObjectMapper objectMapper, PurchasePlanMapper mapper) {
        this.properties = properties;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @Transactional
    public SaleStatSyncResponse sync(String startDate, String endDate) throws Exception {
        Map<String, PurchasePlanEntity> existing = new HashMap<>();
        for (PurchasePlanEntity e : mapper.selectList(null))
            existing.put(e.getPlanSn() + "|" + e.getSku(), e);

        int inserted = 0, updated = 0, offset = 0, length = 500;
        for (int guard = 0; guard < 1000; guard++) {
            Object resp = callApi(startDate, endDate, offset, length);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.convertValue(resp, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> data = (List<Map<String, Object>>) root.get("data");
            if (data == null || data.isEmpty()) break;
            int total = root.get("total") != null ? Integer.parseInt(String.valueOf(root.get("total"))) : 0;

            for (Map<String, Object> item : data) {
                String planSn = str(item.get("plan_sn"));
                String sku = str(item.get("sku"));
                String key = planSn + "|" + sku;

                PurchasePlanEntity e = existing.get(key);
                boolean isNew = (e == null);
                if (isNew) { e = new PurchasePlanEntity(); e.setId(uuid32()); e.setPlanSn(planSn); e.setSku(sku); }

                e.setPpgSn(str(item.get("ppg_sn")));
                e.setProductName(str(item.get("product_name")));
                e.setFnsku(str(item.get("fnsku")));
                e.setPicUrl(str(item.get("pic_url")));
                e.setSupplierId(str(item.get("supplier_id")));
                e.setSupplierName(str(item.get("supplier_name")));
                e.setStatusText(str(item.get("status_text")));
                e.setStatus(intVal(item.get("status")));
                e.setSid(str(item.get("sid")));
                e.setSellerName(str(item.get("seller_name")));
                e.setMarketplace(str(item.get("marketplace")));
                e.setExpectArriveTime(str(item.get("expect_arrive_time")));
                e.setRemark(str(item.get("remark")));
                e.setQuantityPlan(intVal(item.get("quantity_plan")));
                e.setProductId(intVal(item.get("product_id")));
                e.setCgUid(intVal(item.get("cg_uid")));
                e.setCgOptUsername(str(item.get("cg_opt_username")));
                e.setCgBoxPcs(intVal(item.get("cg_box_pcs")));
                e.setIsCombo(intVal(item.get("is_combo")));
                e.setIsAux(intVal(item.get("is_aux")));
                e.setIsRelatedProcessPlan(intVal(item.get("is_related_process_plan")));
                e.setSpu(str(item.get("spu")));
                e.setSpuName(str(item.get("spu_name")));
                e.setCreatorUid(intVal(item.get("creator_uid")));
                e.setCreatorRealName(str(item.get("creator_real_name")));
                e.setWid(intVal(item.get("wid")));
                e.setWarehouseName(str(item.get("warehouse_name")));
                e.setPurchaserId(intVal(item.get("purchaser_id")));
                e.setPurchaserName(str(item.get("purchaser_name")));
                e.setCreateTime(str(item.get("create_time")));
                e.setPlanRemark(str(item.get("plan_remark")));
                e.setAttributeJson(toJson(item.get("attribute")));
                e.setFileJson(toJson(item.get("file")));
                e.setMskuJson(toJson(item.get("msku")));
                e.setPermUidJson(toJson(item.get("perm_uid")));
                e.setPermUsernameJson(toJson(item.get("perm_username")));

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
        body.put("search_field_time", "creator_time");
        body.put("start_date", startDate);
        body.put("end_date", endDate);
        body.put("offset", offset);
        body.put("length", length);

        Map<String, Object> sm = new LinkedHashMap<>(qp);
        sm.putAll(body);
        qp.put("sign", ApiSign.sign(sm, properties.getAppId()));

        return HttpExecutor.create().execute(HttpRequest.builder(Object.class)
                .method(HttpMethod.POST).endpoint(properties.getEndpoint()).path(PATH)
                .queryParams(qp).json(JSON.toJSONString(body))
                .config(new Config().withConnectionTimeout(properties.getConnectTimeout()).withReadTimeout(300000))
                .build()).readEntity(Object.class);
    }

    private String str(Object v) { return v != null ? String.valueOf(v) : ""; }
    private int intVal(Object v) { if (v == null) return 0; try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; } }
    private String toJson(Object v) { return v != null ? JSON.toJSONString(v) : null; }
    private String uuid32() { return UUID.randomUUID().toString().replace("-", ""); }
}
