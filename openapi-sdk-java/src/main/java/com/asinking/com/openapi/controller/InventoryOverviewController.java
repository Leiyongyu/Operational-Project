package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.request.OverviewSearchRequest;
import com.asinking.com.openapi.dto.response.InventoryOverviewItem;
import com.asinking.com.openapi.interceptor.JwtAuthInterceptor;
import com.asinking.com.openapi.service.InventoryOverviewService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 运营组数据看板 — 库存概览接口。
 * admin 看全量数据，user 只看自己负责品牌的数据。
 */
@RestController
@RequestMapping("/api/inventory-overview")
public class InventoryOverviewController {

    private final InventoryOverviewService overviewService;

    /** 构造器注入库存总览服务。 */
    public InventoryOverviewController(InventoryOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    /**
     * 获取库存概览汇总，支持按 SKU / 站点 / 品牌权限筛选。
     */
    @GetMapping
    public Result<PageResult<InventoryOverviewItem>> overview(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "100") long size,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String filterField,
            @RequestParam(required = false) String filterValue,
            HttpServletRequest request) {
        String userId = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));
        String role = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ROLE));
        return Result.ok(overviewService.pageOverview(page, size, null, null, userId, role, sortField, sortOrder, filterField, filterValue));
    }

    /** POST 搜索接口，支持多字段筛选 */
    @PostMapping("/search")
    public Result<PageResult<InventoryOverviewItem>> search(
            @RequestBody OverviewSearchRequest req,
            HttpServletRequest request) {
        String userId = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));
        String role = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ROLE));
        return Result.ok(overviewService.search(req, userId, role));
    }

    /** 搜索字段去重值（实时回显） */
    @GetMapping("/distinct-values")
    public Result<List<String>> distinctValues(
            @RequestParam String field,
            @RequestParam(defaultValue = "") String keyword,
            HttpServletRequest request) {
        String userId = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));
        String role = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ROLE));
        return Result.ok(overviewService.searchDistinctValues(field, keyword, userId, role));
    }

    /** 仅从本地DB重算快照，不拉取外部接口 */
    @PostMapping("/refresh-snapshot")
    public Result<String> refreshSnapshot() {
        overviewService.refreshSnapshot();
        return Result.ok("ok");
    }

    /** 导出 Excel：支持筛选 + 选中行 + 列配置 */
    @PostMapping("/export")
    public void export(@RequestBody Map<String, Object> body,
                       HttpServletRequest request, HttpServletResponse response) throws Exception {
        String userId = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));
        String role = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ROLE));
        @SuppressWarnings("unchecked")
        List<String> rowKeys = (List<String>) body.getOrDefault("rowKeys", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<String> colTitles = (List<String>) body.getOrDefault("colTitles", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<String> colKeys = (List<String>) body.getOrDefault("colKeys", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> filters = (List<Map<String, String>>) body.getOrDefault("filters", Collections.emptyList());

        // 有筛选条件则先筛选
        List<InventoryOverviewItem> all = overviewService.filterOverview(null, null, userId, role);
        if (!filters.isEmpty()) {
            OverviewSearchRequest req = new OverviewSearchRequest();
            List<OverviewSearchRequest.FilterItem> fitems = new ArrayList<>();
            for (Map<String, String> m : filters) {
                OverviewSearchRequest.FilterItem fi = new OverviewSearchRequest.FilterItem();
                fi.setField(m.get("field")); fi.setValue(m.get("value"));
                fitems.add(fi);
            }
            req.setFilters(fitems);
            req.setSize(Integer.MAX_VALUE);
            all = overviewService.search(req, userId, role).getRecords();
        }

        // 有选中行则只导出选中行
        List<InventoryOverviewItem> selected;
        if (!rowKeys.isEmpty()) {
            Set<String> keySet = new HashSet<>(rowKeys);
            selected = all.stream().filter(item ->
                keySet.contains(item.getWarehouseNames() + "|" + item.getSku())).collect(java.util.stream.Collectors.toList());
        } else {
            selected = all;
        }

        Workbook wb = new XSSFWorkbook(); Sheet sheet = wb.createSheet("库存总览");
        Row hr = sheet.createRow(0);
        for (int i = 0; i < Math.min(colKeys.size(), colTitles.size()); i++) hr.createCell(i).setCellValue(colTitles.get(i));
        int ri = 1;
        for (InventoryOverviewItem item : selected) {
            Row row = sheet.createRow(ri++);
            for (int i = 0; i < colKeys.size(); i++) row.createCell(i).setCellValue(String.valueOf(getVal(item, colKeys.get(i))));
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode("库存总览.xlsx", StandardCharsets.UTF_8));
        OutputStream os = response.getOutputStream(); wb.write(os); wb.close(); os.flush();
    }

    private Object getVal(InventoryOverviewItem i, String k) {
        switch (k) {
            case "warehouseNames": return i.getWarehouseNames(); case "sku": return i.getSku();
            case "productName": return i.getProductName(); case "skuLevel": return i.getSkuLevel();
            case "last30DaysProfit": return i.getLast30DaysProfit(); case "returnRate": return i.getReturnRate();
            case "overseasOnway": return i.getOverseasOnway(); case "overseasSellable": return i.getOverseasSellable();
            case "overseasTotal": return i.getOverseasTotal(); case "purchasePendingDelivery": return i.getPurchasePendingDelivery();
            case "localSellable": return i.getLocalSellable(); case "localOnway": return i.getLocalOnway();
            case "purchasePlan": return i.getPurchasePlan(); case "lockNum": return i.getLockNum();
            case "totalInventory": return i.getTotalInventory(); case "last7DaysSales": return i.getLast7DaysSales();
            case "last30DaysSales": return i.getLast30DaysSales(); case "last90DaysSales": return i.getLast90DaysSales();
            case "maxMonthlySales": return i.getMaxMonthlySales(); case "overseasInStockRatio": return i.getOverseasInStockRatio();
            case "overseasTotalRatio": return i.getOverseasTotalRatio(); case "totalInventoryRatio": return i.getTotalInventoryRatio();
            case "lastLocalOutboundTime": return i.getLastLocalOutboundTime(); case "outboundDays": return i.getOutboundDays();
            case "purchaseCycle": return i.getPurchaseCycle(); case "purchaseQuantity": return i.getPurchaseQuantity();
            case "maxMonthlyReplenish": return i.getMaxMonthlyReplenish(); case "owner": return i.getOwner();
            default: return "";
        }
    }
}
