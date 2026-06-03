package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.service.GoodcangClient;
import com.asinking.com.openapi.service.GoodcangSyncService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 谷仓(GoodCang) API 推送订阅回调 + 同步 + 调试接口。
 */
@RestController
@RequestMapping("/api/goodcang")
public class GoodcangCallbackController {

    private static final Logger LOG = LoggerFactory.getLogger(GoodcangCallbackController.class);
    private final GoodcangClient client;
    private final GoodcangSyncService syncService;

    public GoodcangCallbackController(GoodcangClient client, GoodcangSyncService syncService) {
        this.client = client;
        this.syncService = syncService;
    }

    @PostMapping("/callback")
    public Map<String, String> callback(@RequestBody(required = false) String rawBody) {
        LOG.info("谷仓推送收到: {}", rawBody != null ? rawBody.substring(0, Math.min(500, rawBody.length())) : "(空)");
        Map<String, String> resp = new LinkedHashMap<>();
        resp.put("Status", "SUCCESS");
        return resp;
    }

    @GetMapping("/test/grn-list")
    public Object testGrnList(@RequestParam(defaultValue = "2026-05-01 00:00:00") String from,
                              @RequestParam(defaultValue = "2026-05-25 23:59:59") String to) throws Exception {
        Map<String, Object> resp = client.getGrnList(from, to, 1, 5);
        return resp;
    }

    @GetMapping("/test/grn-detail")
    public Object testGrnDetail(@RequestParam String code) throws Exception {
        return client.getGrnDetail(code);
    }

    @PostMapping("/sync-warehouse")
    public Object syncWarehouse() throws Exception {
        return syncService.syncWarehouses();
    }

    @PostMapping("/sync-grn")
    public Object syncGrn(
            @RequestParam(defaultValue = "2026-01-01 00:00:00") String from,
            @RequestParam(required = false) String to) throws Exception {
        if (to == null) to = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return syncService.syncGrn(from, to);
    }

    /** 导出谷仓商品列表为 Excel（全量分页拉取） */
    @GetMapping("/export-product-list")
    public void exportProductList(HttpServletResponse response) throws Exception {
        List<Map<String, Object>> allRows = new ArrayList<>();
        int page = 1;
        while (true) {
            Map<String, Object> resp = client.getProductList(page, 100);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getOrDefault("data", Collections.emptyList());
            if (data.isEmpty()) break;
            allRows.addAll(data);
            if (data.size() < 100) break;
            page++;
        }

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("谷仓商品");
        String[] headers = {"商品SKU", "实收重量(KG)", "实收长(CM)", "实收宽(CM)", "实收高(CM)",
                "中文名称", "英文名称", "重量(KG)", "长(CM)", "宽(CM)", "高(CM)", "商品状态"};
        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (Map<String, Object> r : allRows) {
            Row xr = sheet.createRow(rowIdx++);
            xr.createCell(0).setCellValue(str(r, "product_sku"));
            xr.createCell(1).setCellValue(num(r, "Product_real_weight"));
            xr.createCell(2).setCellValue(num(r, "Product_real_length"));
            xr.createCell(3).setCellValue(num(r, "Product_real_width"));
            xr.createCell(4).setCellValue(num(r, "Product_real_height"));
            xr.createCell(5).setCellValue(str(r, "product_title_cn"));
            xr.createCell(6).setCellValue(str(r, "product_title_en"));
            xr.createCell(7).setCellValue(num(r, "product_weight"));
            xr.createCell(8).setCellValue(num(r, "product_length"));
            xr.createCell(9).setCellValue(num(r, "product_width"));
            xr.createCell(10).setCellValue(num(r, "product_height"));
            xr.createCell(11).setCellValue(str(r, "product_status"));
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" +
                URLEncoder.encode("谷仓商品列表.xlsx", StandardCharsets.UTF_8.toString()));
        OutputStream os = response.getOutputStream();
        wb.write(os);
        wb.close();
        os.flush();
    }

    private String str(Map<String, Object> m, String k) { Object v = m.get(k); return v != null ? String.valueOf(v) : ""; }
    private double num(Map<String, Object> m, String k) { Object v = m.get(k); if (v instanceof Number) return ((Number) v).doubleValue(); if (v != null) try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) {} return 0; }
}
