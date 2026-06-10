package com.asinking.com.openapi.service.impl;

import com.asinking.com.openapi.entity.EbayProductDedupEntity;
import com.asinking.com.openapi.entity.EbayProductListingEntity;
import com.asinking.com.openapi.mapper.mp.EbayProductDedupMapper;
import com.asinking.com.openapi.service.EbayProductDedupService;
import com.asinking.com.openapi.service.EbayProductListingService;
import com.asinking.com.openapi.utils.InventoryUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.util.*;

@Service
public class EbayProductDedupServiceImpl implements EbayProductDedupService {

    private static final Logger LOG = LoggerFactory.getLogger(EbayProductDedupServiceImpl.class);
    private final EbayProductDedupMapper mapper;
    private final EbayProductListingService listingService;

    public EbayProductDedupServiceImpl(EbayProductDedupMapper mapper,
                                       EbayProductListingService listingService) {
        this.mapper = mapper;
        this.listingService = listingService;
    }

    @Override
    public int rebuildFromListing() {
        LOG.info("==== 去重表重建 开始 ====");
        long t = System.currentTimeMillis();

        // 1. 读 ebay_product_listing 全量，按 (sku, 归一化site) 去重，取第一个的 product_name
        Map<String, EbayProductListingEntity> deduped = new LinkedHashMap<>();
        for (EbayProductListingEntity pl : listingService.list()) {
            String sku = pl.getSku();
            if (sku == null || sku.trim().isEmpty()) continue;
            sku = sku.trim();
            String site = InventoryUtils.mapSiteName(
                    pl.getSiteName() != null ? pl.getSiteName().trim() : "");
            if (site.isEmpty()) continue;
            String key = site + "|" + sku;
            deduped.putIfAbsent(key, pl);
        }

        // 2. 加载已有去重记录（保留 oe_number）
        Map<String, String> existingOe = new LinkedHashMap<>();
        for (EbayProductDedupEntity e : mapper.selectList(null)) {
            if (StringUtils.hasText(e.getSite()) && StringUtils.hasText(e.getSku())) {
                existingOe.put(e.getSite() + "|" + e.getSku(),
                        e.getOeNumber() != null ? e.getOeNumber() : "");
            }
        }

        // 3. 删除旧记录，批量插入新记录
        mapper.delete(new LambdaQueryWrapper<>());
        int count = 0;
        List<EbayProductDedupEntity> batch = new ArrayList<>();
        for (Map.Entry<String, EbayProductListingEntity> entry : deduped.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split("\\|", 2);
            EbayProductDedupEntity entity = new EbayProductDedupEntity();
            entity.setSite(parts[0]);
            entity.setSku(parts[1]);
            // 保留已有的 oe_number
            String savedOe = existingOe.get(key);
            entity.setOeNumber(StringUtils.hasText(savedOe) ? savedOe : "");
            // 产品名称：取第一个 listing 的 local_name
            EbayProductListingEntity pl = entry.getValue();
            entity.setProductName(pl.getLocalName() != null ? pl.getLocalName().trim() : "");
            batch.add(entity);
            count++;

            if (batch.size() >= 1000) {
                for (EbayProductDedupEntity e : batch) mapper.insert(e);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            for (EbayProductDedupEntity e : batch) mapper.insert(e);
        }

        LOG.info("==== 去重表重建 完成: {} 条 耗时{}ms ====", count, System.currentTimeMillis() - t);
        return count;
    }

    @Override
    public void saveOe(String site, String sku, String oeNumber) {
        EbayProductDedupEntity existing = mapper.selectOne(
                new LambdaQueryWrapper<EbayProductDedupEntity>()
                        .eq(EbayProductDedupEntity::getSite, site)
                        .eq(EbayProductDedupEntity::getSku, sku));
        if (existing != null) {
            existing.setOeNumber(oeNumber);
            mapper.updateById(existing);
        }
    }

    @Override
    public Map<String, String> batchGetOeNumbers(List<String> keys) {
        Map<String, String> result = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) return result;
        for (EbayProductDedupEntity e : mapper.selectList(null)) {
            if (StringUtils.hasText(e.getSite()) && StringUtils.hasText(e.getSku())) {
                result.put(e.getSite() + "|" + e.getSku(),
                        StringUtils.hasText(e.getOeNumber()) ? e.getOeNumber() : "");
            }
        }
        return result;
    }

    @Override
    public void saveRemark(String site, String sku, String remark) {
        EbayProductDedupEntity existing = mapper.selectOne(
                new LambdaQueryWrapper<EbayProductDedupEntity>()
                        .eq(EbayProductDedupEntity::getSite, site)
                        .eq(EbayProductDedupEntity::getSku, sku));
        if (existing != null) {
            existing.setRemark(remark);
            mapper.updateById(existing);
        }
    }

    @Override
    public Map<String, String> batchGetRemarks(List<String> keys) {
        Map<String, String> result = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) return result;
        for (EbayProductDedupEntity e : mapper.selectList(null)) {
            if (StringUtils.hasText(e.getSite()) && StringUtils.hasText(e.getSku())) {
                result.put(e.getSite() + "|" + e.getSku(),
                        StringUtils.hasText(e.getRemark()) ? e.getRemark() : "");
            }
        }
        return result;
    }

    @Override
    public void saveTrackingCalc(String site, String sku, java.math.BigDecimal trackingPrice,
                                  java.math.BigDecimal profitMargin, java.math.BigDecimal floorPrice) {
        EbayProductDedupEntity e = mapper.selectOne(
                new LambdaQueryWrapper<EbayProductDedupEntity>()
                        .eq(EbayProductDedupEntity::getSite, site)
                        .eq(EbayProductDedupEntity::getSku, sku));
        if (e != null) {
            e.setTrackingPrice(trackingPrice);
            e.setTrackingProfitMargin(profitMargin);
            e.setFloorPrice(floorPrice);
            mapper.updateById(e);
        }
    }

    @Override
    public Map<String, java.math.BigDecimal> batchGetTrackingProfitMargins() {
        Map<String, java.math.BigDecimal> result = new LinkedHashMap<>();
        for (EbayProductDedupEntity e : mapper.selectList(null)) {
            if (StringUtils.hasText(e.getSite()) && StringUtils.hasText(e.getSku()) && e.getTrackingProfitMargin() != null) {
                result.put(e.getSite() + "|" + e.getSku(), e.getTrackingProfitMargin());
            }
        }
        return result;
    }

    @Override
    public Map<String, java.math.BigDecimal> batchGetFloorPrices() {
        Map<String, java.math.BigDecimal> result = new LinkedHashMap<>();
        for (EbayProductDedupEntity e : mapper.selectList(null)) {
            if (StringUtils.hasText(e.getSite()) && StringUtils.hasText(e.getSku()) && e.getFloorPrice() != null) {
                result.put(e.getSite() + "|" + e.getSku(), e.getFloorPrice());
            }
        }
        return result;
    }

    @Override
    public Map<String, java.math.BigDecimal> batchGetTrackingPrices() {
        Map<String, java.math.BigDecimal> result = new LinkedHashMap<>();
        for (EbayProductDedupEntity e : mapper.selectList(null)) {
            if (StringUtils.hasText(e.getSite()) && StringUtils.hasText(e.getSku()) && e.getTrackingPrice() != null) {
                result.put(e.getSite() + "|" + e.getSku(), e.getTrackingPrice());
            }
        }
        return result;
    }

    private static final Map<String, String> SHEET_TO_SITE = new LinkedHashMap<>();
    static {
        SHEET_TO_SITE.put("US", "美国");
        SHEET_TO_SITE.put("UK", "英国");
        SHEET_TO_SITE.put("DE", "德国");
    }

    @Override
    public Map<String, Integer> importProfitRate(byte[] fileBytes) {
        // middleCode → { site → rate }
        Map<String, Map<String, java.math.BigDecimal>> allRates = new LinkedHashMap<>();
        int totalRows = 0;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            DataFormatter df = new DataFormatter();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                String sheetName = sheet.getSheetName().trim();
                String site = SHEET_TO_SITE.get(sheetName);
                if (site == null) site = SHEET_TO_SITE.get(sheetName.toUpperCase());
                if (site == null) { LOG.warn("未知站点sheet: {}，跳过", sheetName); continue; }
                Row hr = sheet.getRow(0);
                if (hr == null) continue;
                int colSku = -1, colRate = -1;
                for (int c = 0; c < hr.getLastCellNum(); c++) {
                    Cell cell = hr.getCell(c);
                    if (cell == null) continue;
                    String h = df.formatCellValue(cell).trim();
                    if (h.equalsIgnoreCase("SKU") || h.contains("产品代码")) colSku = c;
                    else if (h.equalsIgnoreCase("Profit") || h.contains("利润率")) colRate = c;
                }
                if (colSku < 0) colSku = 0;
                if (colRate < 0) colRate = 1;
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    String fullSku = df.formatCellValue(row.getCell(colSku)).trim();
                    String rateStr = df.formatCellValue(row.getCell(colRate)).trim();
                    if (fullSku.isEmpty() || rateStr.isEmpty()) continue;
                    if (rateStr.endsWith("%")) {
                        try { rateStr = new java.math.BigDecimal(rateStr.replace("%", "").trim())
                                .divide(new java.math.BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP).toString(); } catch (Exception ignored) {}
                    }
                    String mid = InventoryUtils.extractMiddleCodeForInventory(fullSku);
                    if (mid.isEmpty()) mid = fullSku;
                    try {
                        allRates.computeIfAbsent(mid, k -> new LinkedHashMap<>())
                                .put(site, new java.math.BigDecimal(rateStr));
                        totalRows++;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) { throw new RuntimeException("解析Excel失败: " + e.getMessage()); }

        // 按 (site, middleCode) 匹配 ebay_product_dedup
        Map<String, EbayProductDedupEntity> dedupByKey = new LinkedHashMap<>();
        for (EbayProductDedupEntity ent : mapper.selectList(null)) {
            if (ent.getSku() == null || ent.getSku().isEmpty() || ent.getSite() == null) continue;
            String mid = InventoryUtils.extractMiddleCodeForInventory(ent.getSku());
            if (!mid.isEmpty()) dedupByKey.put(ent.getSite() + "|" + mid, ent);
        }
        int updated = 0, skipped = 0;
        for (Map.Entry<String, Map<String, java.math.BigDecimal>> entry : allRates.entrySet()) {
            String mid = entry.getKey();
            for (Map.Entry<String, java.math.BigDecimal> se : entry.getValue().entrySet()) {
                EbayProductDedupEntity ent = dedupByKey.get(se.getKey() + "|" + mid);
                if (ent != null) {
                    ent.setProfitRate(se.getValue());
                    mapper.updateById(ent);
                    updated++;
                } else { skipped++; }
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", totalRows);
        result.put("updated", updated);
        result.put("skipped", skipped);
        return result;
    }

    @Override
    public List<EbayProductDedupEntity> listAll() {
        return mapper.selectList(null);
    }

    @Override
    public Map<String, Integer> importReturnRate(byte[] fileBytes) {
        Map<String, java.math.BigDecimal> rateMap = new LinkedHashMap<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Row hr = sheet.getRow(0);
            int colSku = -1, colRate = -1;
            DataFormatter df = new DataFormatter();
            for (int c = 0; c < hr.getLastCellNum(); c++) {
                Cell cell = hr.getCell(c);
                if (cell == null) continue;
                String h = df.formatCellValue(cell).trim();
                if (h.contains("SKU") || h.contains("产品SKU")) colSku = c;
                else if (h.contains("各平台售后率")) colRate = c;
            }
            if (colSku < 0) colSku = 0;
            if (colRate < 0) colRate = 4;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String fullSku = df.formatCellValue(row.getCell(colSku)).trim();
                String rateStr = df.formatCellValue(row.getCell(colRate)).trim();
                if (fullSku.isEmpty() || rateStr.isEmpty()) continue;
                if (rateStr.endsWith("%")) {
                    try { rateStr = new java.math.BigDecimal(rateStr.replace("%", "").trim())
                            .divide(new java.math.BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP).toString(); } catch (Exception ignored) {}
                }
                String mid = InventoryUtils.extractMiddleCodeForInventory(fullSku);
                if (mid.isEmpty()) mid = fullSku;
                try { rateMap.putIfAbsent(mid, new java.math.BigDecimal(rateStr)); } catch (Exception ignored) {}
            }
        } catch (Exception e) { throw new RuntimeException("解析Excel失败: " + e.getMessage()); }
        int updated = 0, skipped = 0;
        Map<String, EbayProductDedupEntity> dedupByMid = new LinkedHashMap<>();
        for (EbayProductDedupEntity ent : mapper.selectList(null)) {
            if (ent.getSku() == null || ent.getSku().isEmpty()) continue;
            String mid = InventoryUtils.extractMiddleCodeForInventory(ent.getSku());
            if (!mid.isEmpty()) dedupByMid.putIfAbsent(mid, ent);
        }
        for (Map.Entry<String, java.math.BigDecimal> e : rateMap.entrySet()) {
            EbayProductDedupEntity ent = dedupByMid.get(e.getKey());
            if (ent != null) { ent.setReturnRate(e.getValue()); mapper.updateById(ent); updated++; }
            else { skipped++; }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", rateMap.size()); result.put("updated", updated); result.put("skipped", skipped);
        return result;
    }
}
