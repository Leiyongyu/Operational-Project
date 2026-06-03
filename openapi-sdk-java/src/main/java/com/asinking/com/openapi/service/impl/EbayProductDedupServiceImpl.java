package com.asinking.com.openapi.service.impl;

import com.asinking.com.openapi.entity.EbayProductDedupEntity;
import com.asinking.com.openapi.entity.EbayProductListingEntity;
import com.asinking.com.openapi.mapper.mp.EbayProductDedupMapper;
import com.asinking.com.openapi.service.EbayProductDedupService;
import com.asinking.com.openapi.service.EbayProductListingService;
import com.asinking.com.openapi.utils.InventoryUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    public List<EbayProductDedupEntity> listAll() {
        return mapper.selectList(null);
    }
}
