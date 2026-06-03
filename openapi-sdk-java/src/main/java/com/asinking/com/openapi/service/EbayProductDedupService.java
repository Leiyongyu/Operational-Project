package com.asinking.com.openapi.service;

import com.asinking.com.openapi.entity.EbayProductDedupEntity;
import java.util.List;
import java.util.Map;

public interface EbayProductDedupService {

    /** 从 ebay_product_listing 全量重建去重表（保留已有的 oe_number） */
    int rebuildFromListing();

    /** 保存/更新 OE 号 */
    void saveOe(String site, String sku, String oeNumber);

    /** 批量查询 OE，返回 site|sku → oeNumber */
    Map<String, String> batchGetOeNumbers(List<String> keys);

    /** 保存/更新备注 */
    void saveRemark(String site, String sku, String remark);

    /** 批量查询备注，返回 site|sku → remark */
    Map<String, String> batchGetRemarks(List<String> keys);

    List<EbayProductDedupEntity> listAll();
}
