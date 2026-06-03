package com.asinking.com.openapi.service;

import com.asinking.com.openapi.entity.LowestPriceRecordEntity;
import java.util.List;
import java.util.Map;

public interface LowestPriceRecordService {

    /**
     * 导入 Excel 文件，按 (site, sku) 分组保留最低价，增量 upsert。
     * 仅当新价格 < 已有价格或记录不存在时才写入。
     * @return { inserted: int, updated: int, skipped: int }
     */
    Map<String, Integer> importFromExcel(byte[] fileBytes, String fileName);

    /** 获取所有最低价记录，返回 site|sku → lowestPrice 映射 */
    Map<String, java.math.BigDecimal> batchGetLowestPrices(List<String> keys);

    List<LowestPriceRecordEntity> listAll();
}
