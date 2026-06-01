package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.entity.EbaySalesEntity;
import com.asinking.com.openapi.mapper.mp.EbaySalesMapper;
import com.asinking.com.openapi.service.InventoryOverviewService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    @PostMapping("/upload")
    @Transactional
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        Workbook wb = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = wb.getSheetAt(0);
        Row headerRow = sheet.getRow(0);

        // 找列索引
        int colOrderNo = -1, colCurrency = -1, colSku = -1, colQty = -1, colPayTime = -1;
        for (Cell c : headerRow) {
            String v = getStr(c);
            switch (v) {
                case "平台订单号": colOrderNo = c.getColumnIndex(); break;
                case "币种": colCurrency = c.getColumnIndex(); break;
                case "库存SKU": colSku = c.getColumnIndex(); break;
                case "购买数量": colQty = c.getColumnIndex(); break;
                case "付款时间": colPayTime = c.getColumnIndex(); break;
            }
        }

        // 加载已有数据去重
        Map<String, EbaySalesEntity> existing = new HashMap<>();
        for (EbaySalesEntity e : mapper.selectList(null))
            existing.put(e.getPlatformOrderNo() + "|" + e.getSku(), e);

        int inserted = 0, updated = 0;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String orderNo = getStr(row.getCell(colOrderNo));
            String sku = getStr(row.getCell(colSku));
            if (orderNo.isEmpty() || sku.isEmpty()) continue;

            String key = orderNo + "|" + sku;
            EbaySalesEntity e = existing.get(key);
            boolean isNew = (e == null);
            if (isNew) {
                e = new EbaySalesEntity();
                e.setId(uuid32());
                e.setPlatformOrderNo(orderNo);
                e.setSku(sku);
                inserted++;
            } else {
                updated++;
            }
            e.setCurrency(getStr(row.getCell(colCurrency)));
            e.setQuantity(parseInt(row.getCell(colQty)));
            e.setPaymentTime(parseDateTime(getStr(row.getCell(colPayTime))));

            if (isNew) mapper.insert(e);
            else mapper.updateById(e);
            existing.put(key, e);
        }
        wb.close();

        Map<String, Object> result = new HashMap<>();
        result.put("inserted", inserted);
        result.put("updated", updated);

        // 销量数据变更后刷新运营数据快照
        try { overviewService.refreshSnapshot(); } catch (Exception ignored) {}

        return Result.ok(result);
    }

    /** 读取单元格字符串值。 */
    private String getStr(Cell c) {
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

    /** 读取单元格整数值，非数字返回 0。 */
    private int parseInt(Cell c) {
        if (c == null) return 0;
        try {
            if (c.getCellType() == CellType.NUMERIC) return (int) c.getNumericCellValue();
            return Integer.parseInt(c.getStringCellValue().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** 解析 yyyy-MM-dd HH:mm:ss 格式的日期时间字符串。 */
    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 生成 32 位无横线 UUID。 */
    private String uuid32() { return UUID.randomUUID().toString().replace("-", ""); }
}
