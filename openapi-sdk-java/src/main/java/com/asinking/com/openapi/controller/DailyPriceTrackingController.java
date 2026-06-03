package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.response.DailyPriceTrackingItem;
import com.asinking.com.openapi.service.DailyPriceTrackingService;
import com.asinking.com.openapi.service.EbayLinkTemplateService;
import com.asinking.com.openapi.service.EbayProductDedupService;
import com.asinking.com.openapi.service.LowestPriceRecordService;
import com.asinking.com.openapi.utils.ExcelUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 每日跟价控制器：分页查询、Excel 导出。
 * 数据独立于补货页，使用专用的 {@link DailyPriceTrackingItem} 响应 DTO。
 */
@RestController
@RequestMapping("/api/daily-price-tracking")
public class DailyPriceTrackingController {

    private final DailyPriceTrackingService service;
    private final LowestPriceRecordService lowestPriceService;
    private final EbayProductDedupService dedupService;
    private final EbayLinkTemplateService linkTemplateService;

    public DailyPriceTrackingController(DailyPriceTrackingService service,
                                        LowestPriceRecordService lowestPriceService,
                                        EbayProductDedupService dedupService,
                                        EbayLinkTemplateService linkTemplateService) {
        this.service = service;
        this.lowestPriceService = lowestPriceService;
        this.dedupService = dedupService;
        this.linkTemplateService = linkTemplateService;
    }

    /** 分页查询 */
    @GetMapping
    public Result<PageResult<DailyPriceTrackingItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String site,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String operator) {
        return Result.ok(service.page(page, size, site, sku, brand, operator));
    }

    /** Excel 导出（导出当前筛选条件下的全部数据） */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String site,
                       @RequestParam(required = false) String sku,
                       @RequestParam(required = false) String brand,
                       @RequestParam(required = false) String operator,
                       HttpServletResponse response) throws Exception {
        PageResult<DailyPriceTrackingItem> result = service.page(1, Integer.MAX_VALUE, site, sku, brand, operator);
        List<DailyPriceTrackingItem> records = result.getRecords();

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("每日跟价");
        String[] headers = {"站点", "SKU等级", "SKU", "产品名称", "我们的链接当前最低价", "跟卖价格",
                "跟卖价格利润率", "底线价", "退货率", "近3天销量", "近7天销量", "近30天销量",
                "近90天销量", "历史1个月的最大月销", "OE号", "售前链接", "售后链接",
                "SKU的海外仓库存", "SKU的海外仓库龄", "SKU库销比", "预估补货量", "品牌", "操作员", "备注"};
        CellStyle headerStyle = ExcelUtils.createHeaderStyle(wb);
        ExcelUtils.writeHeader(sheet, headerStyle, headers);

        int rowIdx = 1;
        for (DailyPriceTrackingItem e : records) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(nvl(e.getSite()));
            row.createCell(1).setCellValue(nvl(e.getSkuLevel()));
            row.createCell(2).setCellValue(nvl(e.getSku()));
            row.createCell(3).setCellValue(nvl(e.getProductName()));
            row.createCell(4).setCellValue(e.getOurLowestPrice() != null ? e.getOurLowestPrice().doubleValue() : 0);
            row.createCell(5).setCellValue(e.getTrackingPrice() != null ? e.getTrackingPrice().doubleValue() : 0);
            row.createCell(6).setCellValue(e.getTrackingProfitMargin() != null ? e.getTrackingProfitMargin().doubleValue() : 0);
            row.createCell(7).setCellValue(e.getFloorPrice() != null ? e.getFloorPrice().doubleValue() : 0);
            row.createCell(8).setCellValue(e.getReturnRate() != null ? e.getReturnRate().doubleValue() : 0);
            row.createCell(9).setCellValue(e.getLast3DaysSales() != null ? e.getLast3DaysSales() : 0);
            row.createCell(10).setCellValue(e.getLast7DaysSales() != null ? e.getLast7DaysSales() : 0);
            row.createCell(11).setCellValue(e.getLast30DaysSales() != null ? e.getLast30DaysSales() : 0);
            row.createCell(12).setCellValue(e.getLast90DaysSales() != null ? e.getLast90DaysSales() : 0);
            row.createCell(13).setCellValue(e.getMaxMonthlySales() != null ? e.getMaxMonthlySales() : 0);
            row.createCell(14).setCellValue(nvl(e.getOeNumber()));
            row.createCell(15).setCellValue(nvl(e.getEbayFrontpageUrl()));
            row.createCell(16).setCellValue(nvl(e.getFrontpageSoldUrl()));
            row.createCell(17).setCellValue(e.getOverseasWarehouseStock() != null ? e.getOverseasWarehouseStock() : 0);
            row.createCell(18).setCellValue(e.getOverseasWarehouseAge() != null ? e.getOverseasWarehouseAge() : 0);
            row.createCell(19).setCellValue(e.getStockSalesRatio() != null ? e.getStockSalesRatio().doubleValue() : 0);
            row.createCell(20).setCellValue(e.getEstimatedReplenish() != null ? e.getEstimatedReplenish() : 0);
            row.createCell(21).setCellValue(nvl(e.getBrand()));
            row.createCell(22).setCellValue(nvl(e.getOperator()));
            row.createCell(23).setCellValue(nvl(e.getRemark()));
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" +
                URLEncoder.encode("每日跟价导出.xlsx", StandardCharsets.UTF_8.toString()));
        OutputStream os = response.getOutputStream();
        wb.write(os);
        wb.close();
        os.flush();
    }

    /** 保存或更新 OE 号（写入 ebay_product_dedup 表），返回生成的链接 */
    @PostMapping("/oe")
    public Result<Map<String, Object>> saveOe(@RequestBody Map<String, String> body) {
        String site = body.get("site");
        String sku = body.get("sku");
        String oeNumber = body.get("oeNumber");
        if (site == null || site.trim().isEmpty() || sku == null || sku.trim().isEmpty()) {
            return Result.fail(com.asinking.com.openapi.common.response.ResultCode.BAD_REQUEST, "site 和 sku 不能为空");
        }
        String oe = oeNumber != null ? oeNumber.trim() : "";
        dedupService.saveOe(site.trim(), sku.trim(), oe);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", true);
        data.put("oeNumber", oe);
        data.put("ebayFrontpageUrl", linkTemplateService.buildPresaleUrl(site.trim(), oe));
        data.put("frontpageSoldUrl", linkTemplateService.buildSoldUrl(site.trim(), oe));
        return Result.ok(data);
    }

    /** 保存 eBay 链接模板（新增或更新，即时生效） */
    @PostMapping("/link-template")
    public Result<Map<String, Object>> saveLinkTemplate(@RequestBody Map<String, String> body) {
        String site = body.get("site");
        String presaleUrl = body.get("presaleUrl");
        String soldUrl = body.get("soldUrl");
        if (site == null || site.trim().isEmpty()) {
            return Result.fail(com.asinking.com.openapi.common.response.ResultCode.BAD_REQUEST, "site 不能为空");
        }
        linkTemplateService.save(site.trim(), presaleUrl, soldUrl);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", true);
        return Result.ok(data);
    }

    /** 删除 eBay 链接模板 */
    @DeleteMapping("/link-template")
    public Result<Map<String, Object>> deleteLinkTemplate(@RequestBody Map<String, String> body) {
        String site = body.get("site");
        if (site == null || site.trim().isEmpty()) {
            return Result.fail(com.asinking.com.openapi.common.response.ResultCode.BAD_REQUEST, "site 不能为空");
        }
        linkTemplateService.delete(site.trim());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", true);
        return Result.ok(data);
    }

    /** 从 ebay_product_listing 重建去重表 */
    @PostMapping("/rebuild-dedup")
    public Result<Map<String, Object>> rebuildDedup() {
        int count = dedupService.rebuildFromListing();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", count);
        return Result.ok(data);
    }

    /** 保存或更新备注（写入 ebay_product_dedup 表） */
    @PostMapping("/remark")
    public Result<Map<String, Object>> saveRemark(@RequestBody Map<String, String> body) {
        String site = body.get("site");
        String sku = body.get("sku");
        String remark = body.get("remark");
        if (site == null || site.trim().isEmpty() || sku == null || sku.trim().isEmpty()) {
            return Result.fail(com.asinking.com.openapi.common.response.ResultCode.BAD_REQUEST, "site 和 sku 不能为空");
        }
        dedupService.saveRemark(site.trim(), sku.trim(), remark != null ? remark : "");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", true);
        return Result.ok(data);
    }

    /** 导入最低价 Excel（按站点+SKU 保留最低价，增量 upsert） */
    @PostMapping("/import-lowest-price")
    public Result<Map<String, Object>> importLowestPrice(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            Map<String, Integer> stats = lowestPriceService.importFromExcel(
                    file.getBytes(), file.getOriginalFilename());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", stats.get("total"));
            data.put("inserted", stats.get("inserted"));
            data.put("updated", stats.get("updated"));
            data.put("skipped", stats.get("skipped"));
            return Result.ok(data);
        } catch (Exception e) {
            return Result.fail(com.asinking.com.openapi.common.response.ResultCode.SERVER_ERROR, e.getMessage());
        }
    }

    /** 查询所有链接模板 */
    @GetMapping("/link-templates")
    public Result<List<Map<String, String>>> getLinkTemplates() {
        List<Map<String, String>> list = new ArrayList<>();
        for (com.asinking.com.openapi.entity.EbayLinkTemplateEntity e : linkTemplateService.listAll()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("site", e.getSite());
            m.put("presaleUrl", e.getPresaleUrl() != null ? e.getPresaleUrl() : "");
            m.put("soldUrl", e.getSoldUrl() != null ? e.getSoldUrl() : "");
            list.add(m);
        }
        return Result.ok(list);
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
