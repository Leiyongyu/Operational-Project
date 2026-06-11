package com.asinking.com.openapi.service;

import com.asinking.com.openapi.entity.GoodcangProductEntity;
import com.asinking.com.openapi.mapper.mp.GoodcangProductMapper;
import com.asinking.com.openapi.utils.InventoryUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GoodcangProductService {

    private static final Logger LOG = LoggerFactory.getLogger(GoodcangProductService.class);
    private final GoodcangClient client;
    private final GoodcangProductMapper mapper;

    public GoodcangProductService(GoodcangClient client, GoodcangProductMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    /** 从谷仓 API 全量拉取商品信息，按中间码清洗，增量 upsert */
    public Map<String, Object> syncFromApi() {
        List<Map<String, Object>> allRows = new ArrayList<>();
        List<Map<String, Object>> skipDetails = new ArrayList<>();
        int page = 1, rawSkipped = 0;
        try {
            while (true) {
                Map<String, Object> resp = client.getProductList(page, 100);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getOrDefault("data", Collections.emptyList());
                if (data.isEmpty()) break;
                allRows.addAll(data);
                if (data.size() < 100) break;
                page++;
            }
        } catch (Exception e) {
            LOG.error("拉取谷仓商品列表失败", e);
            throw new RuntimeException("拉取失败: " + e.getMessage());
        }

        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> row : allRows) {
            String sku = str(row, "product_sku");
            if (sku.isEmpty()) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("reason", "product_sku为空"); skipDetails.add(d); rawSkipped++; continue;
            }
            String mid = InventoryUtils.extractMiddleCodeForInventory(sku);
            if (mid.isEmpty()) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("sku", sku); d.put("reason", "无法提取中间码"); skipDetails.add(d); rawSkipped++; continue;
            }
            deduped.putIfAbsent(mid, row);
        }

        Map<String, GoodcangProductEntity> existing = new LinkedHashMap<>();
        for (GoodcangProductEntity e : mapper.selectList(null)) {
            if (StringUtils.hasText(e.getMiddleCode())) existing.put(e.getMiddleCode(), e);
        }

        int inserted = 0, updated = 0;
        for (Map.Entry<String, Map<String, Object>> e : deduped.entrySet()) {
            String mid = e.getKey(); Map<String, Object> row = e.getValue();
            GoodcangProductEntity ent = existing.get(mid);
            boolean isNew = ent == null;
            if (isNew) ent = new GoodcangProductEntity();
            ent.setMiddleCode(mid);
            ent.setRealWeight(bd(row, "product_weight")); ent.setRealLength(bd(row, "product_length"));
            ent.setRealWidth(bd(row, "product_width")); ent.setRealHeight(bd(row, "product_height"));
            ent.setProductNameCn(str(row, "product_title_cn"));
            BigDecimal vLen = bd(row, "product_length"), vWid = bd(row, "product_width"), vHei = bd(row, "product_height");
            if (vLen != null && vWid != null && vHei != null && vLen.compareTo(BigDecimal.ZERO) > 0)
                ent.setVolume(vLen.multiply(vWid).multiply(vHei).divide(BigDecimal.valueOf(6000), 2, java.math.RoundingMode.HALF_UP));
            ent.setUpdateTime(LocalDateTime.now());
            if (isNew) { ent.setCreateTime(LocalDateTime.now()); mapper.insert(ent); inserted++; }
            else { mapper.updateById(ent); updated++; }
        }

        LOG.info("谷仓商品同步完成: {}条, 新增{}, 更新{}", deduped.size(), inserted, updated);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", deduped.size()); result.put("inserted", inserted); result.put("updated", updated);
        result.put("skipped", rawSkipped); result.put("skipDetails", skipDetails);
        return result;
    }

    /** 从 Excel 导入单价，按 middle_code 完全匹配更新 price 字段 */
    public Map<String, Object> importPriceFromExcel(byte[] fileBytes) {
        Map<String, BigDecimal> priceMap = new LinkedHashMap<>();
        List<Map<String, Object>> skipDetails = new ArrayList<>();
        int parseSkipped = 0;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            int colSku = -1, colPrice = -1;
            Row hr = sheet.getRow(0);
            for (int c = 0; c < hr.getLastCellNum(); c++) {
                String h = hr.getCell(c).getStringCellValue().trim();
                if (h.equalsIgnoreCase("sku")) colSku = c;
                else if (h.equalsIgnoreCase("price") || h.contains("单价")) colPrice = c;
            }
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r); if (row == null) continue;
                String sku = getStr(row, colSku);
                BigDecimal price = getBd(row, colPrice);
                if (sku.isEmpty() || price == null) {
                    Map<String, Object> d = new LinkedHashMap<>(); d.put("row", r + 1); d.put("sku", sku);
                    d.put("reason", "SKU或价格为空"); skipDetails.add(d); parseSkipped++; continue;
                }
                priceMap.put(sku, price);
            }
        } catch (Exception e) { throw new RuntimeException("解析Excel失败: " + e.getMessage()); }

        int updated = 0, skipped = 0;
        for (Map.Entry<String, BigDecimal> e : priceMap.entrySet()) {
            GoodcangProductEntity ent = mapper.selectOne(
                    new LambdaQueryWrapper<GoodcangProductEntity>().eq(GoodcangProductEntity::getMiddleCode, e.getKey()));
            if (ent != null) {
                ent.setPrice(e.getValue()); ent.setUpdateTime(LocalDateTime.now()); mapper.updateById(ent); updated++;
            } else {
                Map<String, Object> d = new LinkedHashMap<>(); d.put("middleCode", e.getKey());
                d.put("price", e.getValue()); d.put("reason", "中间码在goodcang_product表中不存在"); skipDetails.add(d); skipped++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", priceMap.size()); result.put("updated", updated); result.put("skipped", skipped + parseSkipped);
        result.put("skipDetails", skipDetails);
        return result;
    }

    private final DataFormatter fmt = new DataFormatter();

    private String getStr(Row row, int col) {
        if (col < 0) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return fmt.formatCellValue(cell).trim();
    }

    private BigDecimal getBd(Row row, int col) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(cell.getNumericCellValue());
            return new BigDecimal(fmt.formatCellValue(cell).trim());
        } catch (Exception e) { return null; }
    }

    /** 按中间码查单条 */
    public GoodcangProductEntity getByMiddleCode(String middleCode) {
        return mapper.selectOne(new LambdaQueryWrapper<GoodcangProductEntity>()
                .eq(GoodcangProductEntity::getMiddleCode, middleCode));
    }

    /** 批量查询单价，返回 sku_middle → price */
    public Map<String, BigDecimal> batchGetPrices() {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (GoodcangProductEntity e : mapper.selectList(null)) {
            if (StringUtils.hasText(e.getMiddleCode()) && e.getPrice() != null) {
                result.put(e.getMiddleCode(), e.getPrice());
            }
        }
        return result;
    }

    private String str(Map<String, Object> m, String k) { Object v = m.get(k); return v != null ? String.valueOf(v) : ""; }
    private BigDecimal bd(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        if (v != null) try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) {}
        return null;
    }
}
