package com.asinking.com.openapi.service;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.dto.response.DailyPriceTrackingItem;

import java.util.List;
import java.util.Map;

/**
 * 每日跟价服务。
 */
public interface DailyPriceTrackingService {

    PageResult<DailyPriceTrackingItem> page(long page, long size,
                                            String site, String sku, String brand, String operator,
                                            String sortField, String sortOrder);

    PageResult<DailyPriceTrackingItem> search(long page, long size,
                                              List<Map<String, String>> filters,
                                              String sortField, String sortOrder,
                                              String userId, String role);

    void refreshTable();

    List<String> searchDistinctValues(String field, String keyword);
}
