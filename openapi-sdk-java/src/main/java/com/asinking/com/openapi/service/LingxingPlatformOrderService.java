package com.asinking.com.openapi.service;

import com.alibaba.fastjson.JSON;
import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.sdk.core.*;
import com.asinking.com.openapi.sdk.okhttp.HttpExecutor;
import com.asinking.com.openapi.sdk.sign.ApiSign;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;

/**
 * 领星平台订单服务：查询发货单列表。
 */
@Service
public class LingxingPlatformOrderService {

    private static final String SHIPPING_LIST_PATH = "cepf/warehouse/api/openApi/queryShippingListPage";

    private final LingxingProperties properties;
    private final LingxingAuthService authService;

    /** 构造订单服务，注入配置及认证服务 */
    public LingxingPlatformOrderService(LingxingProperties properties,
                                        LingxingAuthService authService) {
        this.properties = properties;
        this.authService = authService;
    }

    /** 测试：获取发货单列表 */
    public Object testFetchOne(String startDate, String endDate) throws Exception {
        List<Map<String, Object>> samples = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();

        for (int i = 0; i < 3; i++) {
            Object resp = callShippingListApi(startDate, endDate, i * 10, 10);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = JSON.parseObject(JSON.toJSONString(resp), Map.class);
            if (!"0".equals(String.valueOf(root.get("code")))) {
                result.put("error_" + i, root.get("code") + ": " + root.get("message"));
                result.put("raw_" + i, root);
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) root.get("data");
            if (data == null) continue;
            List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
            if (records == null || records.isEmpty()) break;

            for (Map<String, Object> item : records) {
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("shippingCode", item.get("shipping_code"));
                sample.put("warehouseId", item.get("warehouse_id"));
                sample.put("gmtCreate", item.get("gmt_create"));
                sample.put("deliveryTime", item.get("delivery_time"));
                List<Map<String, Object>> goods = (List<Map<String, Object>>) item.get("shipping_goods");
                if (goods != null && !goods.isEmpty()) {
                    sample.put("msku", goods.get(0).get("msku"));
                    sample.put("sku", goods.get(0).get("sku"));
                    sample.put("storeName", goods.get(0).get("store_name"));
                    sample.put("platformName", goods.get(0).get("platform_name"));
                }
                samples.add(sample);
            }
        }
        result.put("count", samples.size());
        result.put("samples", samples.subList(0, Math.min(8, samples.size())));
        return result;
    }

    private Object callShippingListApi(String startDate, String endDate, int offset, int length) throws Exception {
        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        queryParams.put("access_token", authService.getAccessToken());
        queryParams.put("app_key", properties.getAppId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("start_time", startDate);
        body.put("end_time", endDate);
        body.put("offset", offset);
        body.put("length", length);

        Map<String, Object> signMap = new LinkedHashMap<>(queryParams);
        signMap.putAll(body);
        String sign = ApiSign.sign(signMap, properties.getAppId());
        queryParams.put("sign", sign);

        HttpRequest<Object> request = HttpRequest.builder(Object.class)
                .method(HttpMethod.POST).endpoint(properties.getEndpoint())
                .path(SHIPPING_LIST_PATH).queryParams(queryParams)
                .json(JSON.toJSONString(body))
                .config(new Config().withConnectionTimeout(properties.getConnectTimeout()).withReadTimeout(300000))
                .build();
        return HttpExecutor.create().execute(request).readEntity(Object.class);
    }
}
