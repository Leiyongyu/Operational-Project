package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.annotation.OperationLog;
import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.common.annotation.OperationLog;
import com.asinking.com.openapi.entity.EbaySalesEntity;
import com.asinking.com.openapi.mapper.mp.EbaySalesMapper;
import com.asinking.com.openapi.service.InventoryOverviewService;
import com.asinking.com.openapi.utils.ExcelUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * eBay 销量数据接口，支持 Excel 上传导入和去重更新。
 */
@RestController
@RequestMapping("/api/ebay-sales")
public class EbaySalesController {

    private final EbaySalesMapper mapper;
    private final InventoryOverviewService overviewService;

    /** 构造器注入 eBay 销量 Mapper 和库存总览服务。 */
    public EbaySalesController(EbaySalesMapper mapper, InventoryOverviewService overviewService) {
        this.mapper = mapper;
        this.overviewService = overviewService;
    }

    /** 上传 eBay 销量 Excel 文件，解析并导入数据，按 (平台订单号+SKU) 去重更新。 */
    @OperationLog(value = "导入", target = "eBay销量导入")
    @PostMapping("/upload")
    @Transactional
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        Workbook wb = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = wb.getSheetAt(0);
        Row headerRow = sheet.getRow(0);

        // 找列索引
        int[] idx = ExcelUtils.findColumnIndexes(headerRow, "平台订单号", "币种", "库存SKU", "购买数量", "付款时间");
        int colOrderNo = idx[0], colCurrency = idx[1], colSku = idx[2], colQty = idx[3], colPayTime = idx[4];

        // 加载已有数据去重
        Map<String, EbaySalesEntity> existing = new HashMap<>();
        for (EbaySalesEntity e : mapper.selectList(null))
            existing.put(e.getPlatformOrderNo() + "|" + e.getSku(), e);

        int inserted = 0, updated = 0, skipped = 0;
        List<Map<String, Object>> skipDetails = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String orderNo = ExcelUtils.getCellString(row.getCell(colOrderNo));
            String sku = ExcelUtils.getCellString(row.getCell(colSku));
            if (orderNo.isEmpty() || sku.isEmpty()) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("row", r + 1); d.put("orderNo", orderNo); d.put("sku", sku);
                d.put("reason", "平台订单号或SKU为空");
                skipDetails.add(d); skipped++; continue;
            }

            String key = orderNo + "|" + sku;
            EbaySalesEntity e = existing.get(key);
            boolean isNew = (e == null);
            if (isNew) {
                e = new EbaySalesEntity();
                e.setId(ExcelUtils.uuid32());
                e.setPlatformOrderNo(orderNo);
                e.setSku(sku);
                inserted++;
            } else {
                updated++;
            }
            e.setCurrency(ExcelUtils.getCellString(row.getCell(colCurrency)));
            e.setQuantity(ExcelUtils.getCellIntOrDefault(row.getCell(colQty)));
            e.setPaymentTime(ExcelUtils.getCellDateTime(row.getCell(colPayTime)));

            if (isNew) mapper.insert(e);
            else mapper.updateById(e);
            existing.put(key, e);
        }
        wb.close();

        Map<String, Object> result = new HashMap<>();
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("skipDetails", skipDetails);

        // 销量数据变更后刷新运营数据快照
        try { overviewService.refreshSnapshot(); } catch (Exception ignored) {}

        return Result.ok(result);
    }
}
