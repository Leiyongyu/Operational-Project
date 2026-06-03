package com.asinking.com.openapi.service;

import com.asinking.com.openapi.config.GoodcangProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 谷仓(GoodCang) API 客户端。
 * 主动调用 API 使用 Basic Auth 头：app-token + app-key。
 */
@Service
public class GoodcangClient {

    private static final String BASE_URL = "https://oms.goodcang.net/public_open";

    private final GoodcangProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 通过配置注入构造客户端 */
    public GoodcangClient(GoodcangProperties properties) {
        this.properties = properties;
    }

    /** 获取入库单列表 */
    public Map<String, Object> getGrnList(String createDateFrom, String createDateTo, int page, int pageSize) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("create_date_from", createDateFrom);
        body.put("create_date_to", createDateTo);
        body.put("page", page);
        body.put("pageSize", pageSize);

        return post(BASE_URL + "/inbound_order/get_grn_list", body);
    }

    /** 获取仓库信息 */
    public Map<String, Object> getWarehouses() throws Exception {
        return post(BASE_URL + "/base_data/get_warehouse", new LinkedHashMap<>());
    }

    /** 获取商品列表（分页） */
    public Map<String, Object> getProductList(int page, int pageSize) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", page);
        body.put("pageSize", pageSize);
        return post(BASE_URL + "/product/get_product_sku_list", body);
    }

    /** 获取入库单明细 */
    public Map<String, Object> getGrnDetail(String receivingCode) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiving_code", receivingCode);

        return post(BASE_URL + "/inbound_order/get_grn_detail", body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("app-token", properties.getAppToken());
        headers.set("app-key", properties.getAppKey());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
        return objectMapper.readValue(resp.getBody(), Map.class);
    }
}
