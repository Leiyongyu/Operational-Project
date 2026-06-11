package com.asinking.com.openapi.controller;
import com.asinking.com.openapi.common.annotation.OperationLog;

import com.asinking.com.openapi.service.EbayLinkTemplateService;
import com.asinking.com.openapi.service.EbayProductDedupService;
import com.asinking.com.openapi.service.GoodcangClient;
import com.asinking.com.openapi.service.GoodcangProductService;
import com.asinking.com.openapi.service.GoodcangSyncService;
import com.asinking.com.openapi.entity.EbayLinkTemplateEntity;
import com.asinking.com.openapi.entity.GoodcangProductEntity;
import com.asinking.com.openapi.utils.InventoryUtils;
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

import jakarta.servlet.http.HttpServletResponse;
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
    private final GoodcangProductService productService;
    private final EbayProductDedupService dedupService;
    private final EbayLinkTemplateService linkTemplateService;

    public GoodcangCallbackController(GoodcangClient client, GoodcangSyncService syncService,
                                       GoodcangProductService productService,
                                       EbayProductDedupService dedupService,
                                       EbayLinkTemplateService linkTemplateService) {
        this.client = client;
        this.syncService = syncService;
        this.productService = productService;
        this.dedupService = dedupService;
        this.linkTemplateService = linkTemplateService;
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

    /** 计算跟卖利润率 + 底线价，并保存跟卖价格 */
    @PostMapping("/calc-tracking")
    public Map<String, Object> calcTracking(@RequestBody Map<String, String> body) {
        String site = body.get("site");
        String sku = body.get("sku");
        String priceStr = body.get("trackingPrice");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);

        // 保存跟卖价格 M（后续计算完会一起更新另外俩字段）
        java.math.BigDecimal M = null;
        if (priceStr != null && !priceStr.isEmpty()) {
            try { M = new java.math.BigDecimal(priceStr); } catch (Exception e) {}
        }

        if (M == null || M.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            resp.put("trackingProfitMargin", null);
            resp.put("floorPrice", null);
            return resp;
        }

        // N: 产品成本 (goodcang_product_info.price by middleCode)
        // 若有横杠取第二段，否则整段就是中间码
        String mid = InventoryUtils.extractMiddleCodeForInventory(sku);
        if (mid.isEmpty() && !sku.isEmpty()) mid = sku;
        GoodcangProductEntity gp = productService.getByMiddleCode(mid);
        if (gp == null || gp.getPrice() == null) {
            resp.put("trackingProfitMargin", null);
            resp.put("floorPrice", null);
            return resp;
        }
        java.math.BigDecimal N = gp.getPrice();          // 产品成本
        java.math.BigDecimal O_orig = gp.getVolume();    // 体积重
        java.math.BigDecimal P = gp.getRealWeight();     // 实重

        double pw = P != null ? P.doubleValue() : 0;
        double vw = O_orig != null ? O_orig.doubleValue() : 0;
        java.math.BigDecimal O = O_orig != null ? O_orig : java.math.BigDecimal.ZERO;

        resp.put("_N", N); resp.put("_O", O); resp.put("_P", P);

        // Q: 实时汇率, T: 目标利润率
        EbayLinkTemplateEntity lt = linkTemplateService.listAll().stream()
                .filter(e -> site.equals(e.getSite())).findFirst().orElse(null);
        if (lt == null || lt.getExchangeRate() == null || lt.getExchangeRate().compareTo(java.math.BigDecimal.ZERO) == 0) {
            resp.put("trackingProfitMargin", null); resp.put("floorPrice", null); return resp;
        }
        java.math.BigDecimal Q = lt.getExchangeRate();
        java.math.BigDecimal T = lt.getProfitRate() != null
                ? java.math.BigDecimal.valueOf(lt.getProfitRate()).divide(java.math.BigDecimal.valueOf(100))
                : java.math.BigDecimal.valueOf(0.08);

        resp.put("_Q", Q); resp.put("_T", T);

        java.math.BigDecimal poundCost, freightCost, platformRate;
        double cw; // 计费重量

        if ("美国".equals(site)) {
            // 美国: 头程=(N+6*O)/Q, 计费=max(0.8*O,P), 平台=0.8575
            cw = Math.max(0.8 * vw, pw);
            platformRate = java.math.BigDecimal.valueOf(0.8575);
            poundCost = N.add(java.math.BigDecimal.valueOf(6).multiply(O))
                    .divide(Q, 6, java.math.RoundingMode.HALF_UP);
            if (Math.max(vw, pw) < 0.5) {
                freightCost = java.math.BigDecimal.valueOf(4)
                        .add(java.math.BigDecimal.valueOf(8).multiply(java.math.BigDecimal.valueOf(cw)));
            } else {
                freightCost = java.math.BigDecimal.valueOf(8)
                        .add(java.math.BigDecimal.valueOf(1.7).multiply(java.math.BigDecimal.valueOf(cw)));
            }
        } else if ("德国".equals(site)) {
            // 德国海运: 头程=(N+8.42*O)/Q, 计费=max(O,P), 平台=0.678, 固定=3.5, 尾程=0.3
            cw = Math.max(vw, pw);
            platformRate = java.math.BigDecimal.valueOf(0.678);
            poundCost = N.add(java.math.BigDecimal.valueOf(8.42).multiply(O))
                    .divide(Q, 6, java.math.RoundingMode.HALF_UP);
            freightCost = java.math.BigDecimal.valueOf(3.5)
                    .add(java.math.BigDecimal.valueOf(0.3).multiply(java.math.BigDecimal.valueOf(cw)));
        } else {
            // 英国: 头程=(N+9.85*O)/Q, 计费=max(O,P), 平台=0.705, 固定=2, 尾程=0.3
            cw = Math.max(vw, pw);
            platformRate = java.math.BigDecimal.valueOf(0.705);
            poundCost = N.add(java.math.BigDecimal.valueOf(9.85).multiply(O))
                    .divide(Q, 6, java.math.RoundingMode.HALF_UP);
            freightCost = java.math.BigDecimal.valueOf(2)
                    .add(java.math.BigDecimal.valueOf(0.3).multiply(java.math.BigDecimal.valueOf(cw)));
        }

        java.math.BigDecimal netIncome = M.multiply(platformRate);
        java.math.BigDecimal profit = netIncome.subtract(poundCost).subtract(freightCost);
        java.math.BigDecimal margin = M.compareTo(java.math.BigDecimal.ZERO) > 0
                ? profit.divide(M, 6, java.math.RoundingMode.HALF_UP) : java.math.BigDecimal.ZERO;
        resp.put("trackingProfitMargin", margin);

        java.math.BigDecimal denominator = platformRate.subtract(T);
        if (denominator.compareTo(java.math.BigDecimal.ZERO) > 0) {
            java.math.BigDecimal floorPrice = poundCost.add(freightCost)
                    .divide(denominator, 2, java.math.RoundingMode.HALF_UP);
            resp.put("floorPrice", floorPrice);
        } else {
            resp.put("floorPrice", null);
        }

        // 保存三个字段到 ebay_product_dedup
        dedupService.saveTrackingCalc(site, sku, M,
                (java.math.BigDecimal) resp.get("trackingProfitMargin"),
                (java.math.BigDecimal) resp.get("floorPrice"));

        return resp;
    }

    /** 保存跟卖价格（按 site+sku 更新 ebay_product_dedup.tracking_price） */
    @PostMapping("/save-tracking-price")
    public Object saveTrackingPrice(@RequestBody Map<String, String> body) {
        String site = body.get("site");
        String sku = body.get("sku");
        String price = body.get("trackingPrice");
        if (site == null || sku == null) return Map.of("success", false);
        dedupService.saveTrackingCalc(site, sku, price != null && !price.isEmpty() ? new java.math.BigDecimal(price) : null, null, null);
        return Map.of("success", true);
    }

    /** 导入近30天利润率 Excel（按中间码匹配更新 ebay_product_dedup.profit_rate） */
    @OperationLog(value = "导入", target = "利润率导入")
    @PostMapping("/import-profit-rate")
    public Object importProfitRate(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        return dedupService.importProfitRate(file.getBytes());
    }

    /** 导入退货率 Excel（按中间码匹配更新 ebay_product_dedup.return_rate） */
    @OperationLog(value = "导入", target = "退货率导入")
    @PostMapping("/import-return-rate")
    public Object importReturnRate(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        return dedupService.importReturnRate(file.getBytes());
    }

    /** 导入商品单价 Excel（按 middle_code 匹配更新 price） */
    @OperationLog(value = "导入", target = "商品单价导入")
    @PostMapping("/import-price")
    public Map<String, Object> importPrice(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        Map<String, Object> stats = productService.importPriceFromExcel(file.getBytes());
        stats.put("success", true);
        return stats;
    }

    /** 同步谷仓商品到数据库（按中间码清洗，增量 upsert） */
    @OperationLog(value = "同步", target = "谷仓-商品信息同步")
    @PostMapping("/sync-product")
    public Object syncProduct() {
        return productService.syncFromApi();
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
        String[] headers = {"商品SKU", "预报重量(KG)", "预报长(CM)", "预报宽(CM)", "预报高(CM)", "中文名称"};
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
            xr.createCell(1).setCellValue(num(r, "product_weight"));
            xr.createCell(2).setCellValue(num(r, "product_length"));
            xr.createCell(3).setCellValue(num(r, "product_width"));
            xr.createCell(4).setCellValue(num(r, "product_height"));
            xr.createCell(5).setCellValue(str(r, "product_title_cn"));
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
