package com.asinking.com.openapi.service;

import com.alibaba.fastjson.JSON;
import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.dto.response.PurchasePlanCreateResponse;
import com.asinking.com.openapi.sdk.core.*;
import com.asinking.com.openapi.sdk.okhttp.HttpExecutor;
import com.asinking.com.openapi.sdk.sign.ApiSign;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

/**
 * 领星采购计划创建服务，支持 Excel 上传和 JSON 两种方式创建采购计划。
 */
@Service
public class LingxingPurchasePlanService {
    private static final Logger LOG = LoggerFactory.getLogger(LingxingPurchasePlanService.class);
    private static final String PATH = "erp/sc/routing/data/local_inventory/createPurchasePlan";

    private final LingxingProperties properties;
    private final LingxingAuthService authService;
    private final ObjectMapper objectMapper;

    public LingxingPurchasePlanService(LingxingProperties properties, LingxingAuthService authService,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析上传的 Excel 并调用领星创建采购计划。
     */
    public PurchasePlanCreateResponse uploadAndCreate(MultipartFile file) throws Exception {
        List<Map<String, Object>> items = parseExcel(file.getInputStream());
        return callApi(items);
    }

    /**
     * 直接使用 JSON 数组创建采购计划。
     */
    public PurchasePlanCreateResponse createFromJson(List<Map<String, Object>> data) throws Exception {
        return callApi(data);
    }

    /** 解析采购计划 Excel 文件，返回行数据列表。 */
    List<Map<String, Object>> parseExcel(InputStream inputStream) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        Workbook wb = new XSSFWorkbook(inputStream);
        Sheet sheet = wb.getSheetAt(0);

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String sku = strVal(row, 0);
            if (sku.isEmpty()) continue;

            int perBox = intVal(row, 4);   // 单箱数量 (E)
            int boxes = intVal(row, 5);    // 箱数 (F)

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sku", sku);
            putIfNotEmpty(item, "sid", strVal(row, 1));
            putIfNotEmpty(item, "fnsku", strVal(row, 2));
            putIntIfNotNull(item, "supplier_id", intOrNull(row, 3));
            putIntIfNotNull(item, "wid", intOrNull(row, 6));
            putIntIfNotNull(item, "purchaser_id", intOrNull(row, 7));
            item.put("quantity_plan", perBox * boxes > 0 ? perBox * boxes : intVal(row, 8));
            putIfNotEmpty(item, "expect_arrive_time", strVal(row, 9));
            putIfNotEmpty(item, "remark", strVal(row, 10));

            items.add(item);
        }
        wb.close();
        return items;
    }

    /** 调用领星创建采购计划 API，校验必填字段。 */
    private PurchasePlanCreateResponse callApi(List<Map<String, Object>> data) throws Exception {
        // 必填字段校验：sku、wid、quantity_plan、remark
        if (data == null || data.isEmpty()) throw new IllegalArgumentException("采购计划数据不能为空");
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> item = data.get(i);
            int row = i + 1;
            if (item.get("sku") == null || String.valueOf(item.get("sku")).trim().isEmpty())
                throw new IllegalArgumentException("第" + row + "行 sku 不能为空");
            if (item.get("wid") == null)
                throw new IllegalArgumentException("第" + row + "行 仓库 不能为空");
            if (item.get("quantity_plan") == null)
                throw new IllegalArgumentException("第" + row + "行 计划采购数量 不能为空");
            if (item.get("remark") == null || String.valueOf(item.get("remark")).trim().isEmpty())
                throw new IllegalArgumentException("第" + row + "行 产品备注 不能为空");
        }

        Map<String, Object> qp = new LinkedHashMap<>();
        qp.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        qp.put("access_token", authService.getAccessToken());
        qp.put("app_key", properties.getAppId());

        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("data", data);

        String body = JSON.toJSONString(bodyMap);
        Map<String, Object> sm = new LinkedHashMap<>(qp);
        sm.put("data", JSON.toJSONString(data));
        qp.put("sign", ApiSign.sign(sm, properties.getAppId()));

        LOG.info("创建采购计划请求体: {}", body);

        Object resp = HttpExecutor.create().execute(HttpRequest.builder(Object.class)
                .method(HttpMethod.POST).endpoint(properties.getEndpoint()).path(PATH)
                .queryParams(qp).json(body)
                .config(new Config().withConnectionTimeout(properties.getConnectTimeout()).withReadTimeout(120000))
                .build()).readEntity(Object.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> respMap = objectMapper.convertValue(resp, Map.class);

        LOG.info("领星采购计划响应: {}", JSON.toJSONString(respMap));

        String code = String.valueOf(respMap.getOrDefault("code", ""));
        if (!"0".equals(code)) {
            String msg = String.valueOf(respMap.getOrDefault("message", "未知错误"));
            Object errorDetails = respMap.get("error_details");
            throw new RuntimeException("领星创建采购计划失败: " + msg + (errorDetails != null ? " " + errorDetails : ""));
        }

        Object dataObj = respMap.get("data");
        if (dataObj == null) {
            throw new RuntimeException("领星返回 data 为空");
        }
        return objectMapper.convertValue(dataObj, PurchasePlanCreateResponse.class);
    }

    /** 读取单元格字符串值。 */
    private String strVal(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) return "";
        switch (c.getCellType()) {
            case STRING: return c.getStringCellValue().trim();
            case NUMERIC: {
                double v = c.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
                return String.valueOf(v);
            }
            default: return "";
        }
    }

    /** 读取单元格整数值，空或异常返回 0。 */
    private int intVal(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) return 0;
        try {
            if (c.getCellType() == CellType.NUMERIC) return (int) c.getNumericCellValue();
            return Integer.parseInt(c.getStringCellValue().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** 读取单元格整数值，空或异常返回 null。 */
    private Integer intOrNull(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC) return (int) c.getNumericCellValue();
            String s = c.getStringCellValue().trim();
            return s.isEmpty() ? null : Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 仅当值非空时放入 map。 */
    private void putIfNotEmpty(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isEmpty()) m.put(k, v);
    }

    /** 仅当值非 null 时放入 map。 */
    private void putIntIfNotNull(Map<String, Object> m, String k, Integer v) {
        if (v != null) m.put(k, v);
    }
}
