package com.asinking.com.openapi.service.impl;

import com.asinking.com.openapi.entity.LowestPriceRecordEntity;
import com.asinking.com.openapi.mapper.mp.LowestPriceRecordMapper;
import com.asinking.com.openapi.service.LowestPriceRecordService;
import com.asinking.com.openapi.utils.InventoryUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LowestPriceRecordServiceImpl implements LowestPriceRecordService {

    private static final Logger LOG = LoggerFactory.getLogger(LowestPriceRecordServiceImpl.class);
    private final LowestPriceRecordMapper mapper;

    public LowestPriceRecordServiceImpl(LowestPriceRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<String, Integer> importFromExcel(byte[] fileBytes, String fileName) {
        int inserted = 0, updated = 0, skipped = 0;

        // 1. 解析 Excel → 按 (site, sku) 取最低价
        Map<String, ExcelRow> lowestByKey = new LinkedHashMap<>(); // "site|sku" → ExcelRow
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new IllegalArgumentException("Excel 文件无表头");

            // 解析表头，找 SKU / 站点 / 价格 / Item Number 列索引
            int colSku = -1, colSite = -1, colPrice = -1, colItemNo = -1;
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                if (cell == null) continue;
                String h = cell.getStringCellValue().trim();
                if ("SKU".equalsIgnoreCase(h)) colSku = c;
                else if ("站点".equals(h) || "Site".equalsIgnoreCase(h)) colSite = c;
                else if ("价格".equals(h) || "Price".equalsIgnoreCase(h)) colPrice = c;
                else if ("Item Number".equalsIgnoreCase(h)) colItemNo = c;
            }
            if (colSku < 0 || colSite < 0 || colPrice < 0) {
                throw new IllegalArgumentException("Excel 缺少必要列：SKU / 站点 / 价格，当前表头=" + getHeaders(headerRow));
            }

            // 遍历数据行，按 (site, sku) 保留最低价
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String sku = getCellStr(row, colSku);
                String site = getCellStr(row, colSite);
                String itemNo = getCellStr(row, colItemNo);
                BigDecimal price = getCellDecimal(row, colPrice);
                if (sku.isEmpty() || site.isEmpty() || price == null) continue;

                // 清洗 SKU：OTH-230027-0310 → OTH-230027, 2PC-OTH-230027-0310 → 2PC-OTH-230027
                sku = InventoryUtils.extractBaseSku(sku);
                // 清洗站点：eBay汽配 → 美国
                site = InventoryUtils.mapSiteName(site);
                if (sku.isEmpty() || site.isEmpty()) continue;

                String key = site + "|" + sku;
                ExcelRow exist = lowestByKey.get(key);
                if (exist == null || price.compareTo(exist.price) < 0) {
                    lowestByKey.put(key, new ExcelRow(sku, site, itemNo, price));
                }
            }
        } catch (Exception e) {
            LOG.error("解析 Excel 失败: {}", fileName, e);
            throw new RuntimeException("解析 Excel 失败: " + e.getMessage());
        }

        // 2. 加载已有记录
        Map<String, LowestPriceRecordEntity> existingMap = new LinkedHashMap<>();
        for (LowestPriceRecordEntity e : mapper.selectList(null)) {
            if (StringUtils.hasText(e.getSite()) && StringUtils.hasText(e.getSku())) {
                existingMap.put(e.getSite() + "|" + e.getSku(), e);
            }
        }

        // 3. 增量 upsert（仅当新价格更低时才写）
        for (Map.Entry<String, ExcelRow> entry : lowestByKey.entrySet()) {
            String key = entry.getKey();
            ExcelRow row = entry.getValue();
            LowestPriceRecordEntity existing = existingMap.get(key);

            if (existing != null) {
                // 已有记录：只在价格更低时更新
                if (row.price.compareTo(existing.getLowestPrice()) < 0) {
                    existing.setLowestPrice(row.price);
                    existing.setItemNumber(row.itemNumber);
                    existing.setUploadTime(LocalDateTime.now());
                    mapper.updateById(existing);
                    updated++;
                } else {
                    skipped++;
                }
            } else {
                // 新记录：直接插入
                LowestPriceRecordEntity entity = new LowestPriceRecordEntity();
                entity.setSite(row.site);
                entity.setSku(row.sku);
                entity.setItemNumber(row.itemNumber);
                entity.setLowestPrice(row.price);
                entity.setUploadTime(LocalDateTime.now());
                mapper.insert(entity);
                inserted++;
            }
        }

        LOG.info("最低价导入完成: {} 条, 新增{}, 更新{}, 跳过{}", fileName, inserted, updated, skipped);
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", lowestByKey.size());
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("skipped", skipped);
        return result;
    }

    @Override
    public Map<String, BigDecimal> batchGetLowestPrices(List<String> keys) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) return result;
        for (LowestPriceRecordEntity e : mapper.selectList(null)) {
            if (StringUtils.hasText(e.getSite()) && StringUtils.hasText(e.getSku())
                    && e.getLowestPrice() != null) {
                result.put(e.getSite() + "|" + e.getSku(), e.getLowestPrice());
            }
        }
        return result;
    }

    @Override
    public List<LowestPriceRecordEntity> listAll() {
        return mapper.selectList(null);
    }

    // ===== 工具方法 =====

    private String getCellStr(Row row, int col) {
        if (col < 0) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private BigDecimal getCellDecimal(Row row, int col) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            cell.setCellType(CellType.STRING);
            return new BigDecimal(cell.getStringCellValue().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String getHeaders(Row headerRow) {
        List<String> h = new ArrayList<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            h.add(cell != null ? cell.getStringCellValue() : "");
        }
        return String.join(",", h);
    }

    private static class ExcelRow {
        String sku, site, itemNumber;
        BigDecimal price;
        ExcelRow(String s, String t, String i, BigDecimal p) { sku = s; site = t; itemNumber = i; price = p; }
    }
}
