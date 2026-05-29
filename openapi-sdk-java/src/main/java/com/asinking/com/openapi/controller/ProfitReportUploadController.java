package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.entity.ProfitReportEntity;
import com.asinking.com.openapi.mapper.mp.ProfitReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

/**
 * 利润报表 Excel 上传接口：所有列以 JSON 存入 raw_data，关键列建索引。
 */
@RestController
@RequestMapping("/api/profit-report")
public class ProfitReportUploadController {

    private final ProfitReportMapper mapper;

    /** 构造器注入利润报表 Mapper。 */
    public ProfitReportUploadController(ProfitReportMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 上传 Excel，按 (msku, ship_time, store_name, country_code) 增量 upsert。
     */
    @PostMapping("/upload")
    @Transactional
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        Workbook wb = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = wb.getSheetAt(0);
        int totalRows = sheet.getLastRowNum();

        Row headerRow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        for (Cell c : headerRow) {
            String v = getStringValue(c);
            headers.add(v != null ? v.trim() : "");
        }

        // 解析 Excel
        List<ProfitReportEntity> newList = new ArrayList<>();
        Set<String> rowKeys = new HashSet<>();
        for (int r = 1; r <= totalRows; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Map<String, String> rowData = new HashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String h = headers.get(c);
                if (h.isEmpty()) continue;
                rowData.put(h, getStringValue(row.getCell(c)));
            }

            String mskuVal = rowData.get("msku");
            if (mskuVal == null || mskuVal.isEmpty()) continue;

            String shipTime = rowData.get("发货时间");
            String storeName = rowData.get("店铺名称");
            String countryCode = rowData.get("国家代码");

            // 批内去重
            String rowKey = mskuVal + "|" + (shipTime != null ? shipTime : "") + "|" + (storeName != null ? storeName : "") + "|" + (countryCode != null ? countryCode : "");
            if (!rowKeys.add(rowKey)) continue;

            ProfitReportEntity e = new ProfitReportEntity();
            e.setMsku(mskuVal);
            e.setShipTime(shipTime);
            e.setStoreName(storeName);
            e.setCountryCode(countryCode);
            e.setCurrencyCode(rowData.get("币种"));
            e.setPlatform(rowData.get("平台"));
            e.setVolume(parseInt(rowData.get("数量")));
            e.setSalesAmount(parseDecimal(rowData.get("销售额")));
            e.setGrossProfit(parseDecimal(rowData.get("毛利润")));
            e.setGrossMargin(parsePercent(rowData.get("毛利率")));
            e.setPurchaseCost(parseDecimal(rowData.get("销售额对应采购成本")));
            e.setLogisticsCost(parseDecimal(rowData.get("销售额对应头程成本")));
            e.setFileName(fileName);
            newList.add(e);
        }
        wb.close();

        // 查询已有数据
        List<ProfitReportEntity> existingAll = mapper.selectList(null);
        Map<String, ProfitReportEntity> existingMap = new HashMap<>();
        for (ProfitReportEntity e : existingAll) {
            String key = buildKey(e.getMsku(), e.getShipTime(), e.getStoreName(), e.getCountryCode());
            existingMap.put(key, e);
        }

        int inserted = 0, updated = 0;
        for (ProfitReportEntity e : newList) {
            String key = buildKey(e.getMsku(), e.getShipTime(), e.getStoreName(), e.getCountryCode());
            ProfitReportEntity exist = existingMap.get(key);
            if (exist != null) {
                exist.setFileName(e.getFileName());
                exist.setPlatform(e.getPlatform());
                exist.setVolume(e.getVolume());
                exist.setSalesAmount(e.getSalesAmount());
                exist.setGrossProfit(e.getGrossProfit());
                exist.setCurrencyCode(e.getCurrencyCode());
                exist.setGrossMargin(e.getGrossMargin());
                exist.setPurchaseCost(e.getPurchaseCost());
                exist.setLogisticsCost(e.getLogisticsCost());
                mapper.updateById(exist);
                updated++;
            } else {
                e.setId(uuid32());
                mapper.insert(e);
                existingMap.put(key, e);
                inserted++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("file_name", fileName);
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("total_rows", totalRows);
        return Result.ok(result);
    }

    /** 构建 upsert 唯一键 (msku|shipTime|storeName|countryCode)。 */
    private String buildKey(String msku, String shipTime, String storeName, String countryCode) {
        return (msku != null ? msku : "") + "|" + (shipTime != null ? shipTime : "") + "|"
             + (storeName != null ? storeName : "") + "|" + (countryCode != null ? countryCode : "");
    }

    /** 从 Excel 单元格读取字符串值。 */
    private String getStringValue(Cell c) {
        if (c == null) return null;
        switch (c.getCellType()) {
            case STRING: return c.getStringCellValue().trim();
            case NUMERIC: return String.valueOf(c.getNumericCellValue());
            case BOOLEAN: return String.valueOf(c.getBooleanCellValue());
            case FORMULA:
                try { return String.valueOf(c.getNumericCellValue()); }
                catch (Exception e) { return c.getStringCellValue().trim(); }
            default: return null;
        }
    }

    /** 安全解析整数字符串，解析失败返回 0。 */
    private int parseInt(String v) {
        if (v == null || v.isEmpty()) return 0;
        try { return (int) Double.parseDouble(v.replace(",", "")); } catch (Exception e) { return 0; }
    }

    /** 安全解析数字字符串为 BigDecimal，解析失败返回 ZERO。 */
    private BigDecimal parseDecimal(String v) {
        if (v == null || v.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(v.replace(",", "").replace("%", "")); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    /** 安全解析百分比字符串（如 "15%" -> 0.15），解析失败返回 ZERO。 */
    private BigDecimal parsePercent(String v) {
        if (v == null || v.isEmpty()) return BigDecimal.ZERO;
        try {
            String s = v.replace(",", "").replace("%", "");
            return new BigDecimal(s).divide(BigDecimal.valueOf(100), 6, BigDecimal.ROUND_HALF_UP);
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    /** 生成不带横线的 32 位随机 UUID。 */
    private String uuid32() { return UUID.randomUUID().toString().replace("-", ""); }
}
