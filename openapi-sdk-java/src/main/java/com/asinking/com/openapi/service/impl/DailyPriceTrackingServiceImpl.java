package com.asinking.com.openapi.service.impl;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.service.EbayLinkTemplateService;

import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.dto.response.DailyPriceTrackingItem;
import com.asinking.com.openapi.entity.*;
import com.asinking.com.openapi.mapper.mp.*;
import com.asinking.com.openapi.service.*;
import com.asinking.com.openapi.utils.InventoryUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * 每日跟价服务实现：独立于补货页的数据查询与计算。
 * 直接从各数据源查询，使用 {@link InventoryUtils} 共享工具方法。
 * 预估补货量直接从补货页的采购数量匹配（按站点+去除PC前缀的SKU）。
 */
@Service
public class DailyPriceTrackingServiceImpl implements DailyPriceTrackingService {

    private static final Logger LOG = LoggerFactory.getLogger(DailyPriceTrackingServiceImpl.class);
    private final LingxingProperties lingxingProperties;
    private final WarehouseService warehouseService;
    private final WarehouseInventoryDetailService inventoryService;
    private final EbaySalesMapper ebaySalesMapper;
    private final BrandOwnerService brandOwnerService;
    private final GoodcangGrnListMapper grnListMapper;
    private final GoodcangGrnDetailMapper grnDetailMapper;
    private final GoodcangWarehouseMapper gcWarehouseMapper;
    private final InventoryOverviewService overviewService;
    private final LowestPriceRecordService lowestPriceService;
    private final EbayProductDedupService dedupService;
    private final EbayLinkTemplateService linkTemplateService;
    private final DailyPriceTrackingCacheMapper cacheMapper;
    public DailyPriceTrackingServiceImpl(LingxingProperties lingxingProperties,
                                         WarehouseService warehouseService,
                                         WarehouseInventoryDetailService inventoryService,
                                         EbaySalesMapper ebaySalesMapper,
                                         BrandOwnerService brandOwnerService,
                                         GoodcangGrnListMapper grnListMapper,
                                         GoodcangGrnDetailMapper grnDetailMapper,
                                         GoodcangWarehouseMapper gcWarehouseMapper,
                                         InventoryOverviewService overviewService,
                                         LowestPriceRecordService lowestPriceService,
                                         EbayProductDedupService dedupService,
                                         EbayLinkTemplateService linkTemplateService,
                                         DailyPriceTrackingCacheMapper cacheMapper) {
        this.lingxingProperties = lingxingProperties;
        this.warehouseService = warehouseService;
        this.inventoryService = inventoryService;
        this.ebaySalesMapper = ebaySalesMapper;
        this.brandOwnerService = brandOwnerService;
        this.grnListMapper = grnListMapper;
        this.grnDetailMapper = grnDetailMapper;
        this.gcWarehouseMapper = gcWarehouseMapper;
        this.overviewService = overviewService;
        this.lowestPriceService = lowestPriceService;
        this.dedupService = dedupService;
        this.linkTemplateService = linkTemplateService;
        this.cacheMapper = cacheMapper;
    }

    @Override
    public PageResult<DailyPriceTrackingItem> page(long page, long size,
                                                   String site, String sku, String brand, String operator) {
        // 1. 优先从缓存表读取，表为空则实时计算
        Long tableCount = cacheMapper.selectCount(null);
        List<DailyPriceTrackingItem> allRows;
        if (tableCount != null && tableCount > 0) {
            allRows = cacheMapper.selectList(null).stream().map(this::entityToDto).collect(Collectors.toList());
        } else {
            allRows = computeDailyPriceTracking();
        }

        // 2. 内存筛选
        List<DailyPriceTrackingItem> filtered = allRows.stream()
                .filter(e -> matchMulti(site, e.getSite()))
                .filter(e -> matchContains(sku, e.getSku()))
                .filter(e -> matchContains(brand, e.getBrand()))
                .filter(e -> matchContains(operator, e.getOperator()))
                .collect(Collectors.toList());

        // 3. 内存分页
        long p = page <= 0 ? 1 : page;
        long s = size <= 0 ? 20 : Math.min(size, 200);
        long total = filtered.size();
        int from = (int) ((p - 1) * s);
        int to = (int) Math.min(from + s, total);
        List<DailyPriceTrackingItem> pageRecords = from < total
                ? filtered.subList(from, to)
                : Collections.emptyList();

        return new PageResult<>(total, p, s, pageRecords);
    }

    // ====================================================================
    // 核心计算逻辑
    // ====================================================================

    // ====================================================================
    // 核心计算逻辑（实时，无快照）
    // ====================================================================

    /**
     * 独立计算每日跟价数据，不再复用 InventoryOverviewService。
     * 数据流程：基准 SKU → 库存 → 销量 → 出库时间 → 组装。
     */
    private List<DailyPriceTrackingItem> computeDailyPriceTracking() {

        List<Integer> inventoryWids = parseInventoryWids();

        // ==== 1. 仓库 → 站点映射 ====
        Map<Integer, WarehouseEntity> warehouseMap = warehouseService.lambdaQuery()
                .in(WarehouseEntity::getWid, inventoryWids)
                .ne(WarehouseEntity::getWid, 1194)
                .list().stream()
                .collect(Collectors.toMap(WarehouseEntity::getWid, e -> e, (a, b) -> a));

        Map<Integer, String> widToSite = new HashMap<>();
        for (Map.Entry<Integer, WarehouseEntity> e : warehouseMap.entrySet()) {
            widToSite.put(e.getKey(), InventoryUtils.whNameToSite(e.getValue().getName()));
        }

        // ==== 2. 基准 (baseSku → 站点列表 + 产品名 + OE) from ebay_product_dedup（已去重） ====
        Map<String, String> skuProductNameMap = new LinkedHashMap<>();
        Map<String, Set<String>> skuSitesMap = new LinkedHashMap<>(); // baseSku → Set<siteLabel>
        for (EbayProductDedupEntity dedup : dedupService.listAll()) {
            String baseSku = dedup.getSku();
            String siteLabel = dedup.getSite();
            if (baseSku == null || baseSku.isEmpty() || siteLabel == null || siteLabel.isEmpty()) continue;
            if (!skuProductNameMap.containsKey(baseSku) && dedup.getProductName() != null) {
                skuProductNameMap.put(baseSku, dedup.getProductName().trim());
            }
            skuSitesMap.computeIfAbsent(baseSku, k -> new LinkedHashSet<>()).add(siteLabel);
        }

        // ==== 3. 海外可售库存 (仅 type=3 海外仓) ====
        Map<String, Integer> overseasStockMap = new LinkedHashMap<>(); // "baseSku|siteLabel" → stock
        for (WarehouseInventoryDetailEntity d : inventoryService.lambdaQuery()
                .in(WarehouseInventoryDetailEntity::getWid, inventoryWids).list()) {
            WarehouseEntity wh = warehouseMap.get(d.getWid());
            if (wh == null || wh.getType() == null || wh.getType() != 3) continue;
            String baseSku = InventoryUtils.extractBaseSku(d.getSku());
            if (baseSku.isEmpty()) continue;
            String siteLabel = widToSite.getOrDefault(d.getWid(), "");
            if (siteLabel.isEmpty()) continue;
            int sellable = d.getProductValidNum() != null ? d.getProductValidNum() : 0;
            overseasStockMap.merge(baseSku + "|" + siteLabel, sellable, Integer::sum);
        }

        // ==== 4. 销量 from ebay_sales ====
        LocalDate today = LocalDate.now();
        Map<String, Integer> sales3d = new LinkedHashMap<>();
        Map<String, Integer> sales7d = new LinkedHashMap<>();
        Map<String, Integer> sales30d = new LinkedHashMap<>();
        Map<String, Integer> sales90d = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> monthlySalesMap = new LinkedHashMap<>();

        for (EbaySalesEntity s : ebaySalesMapper.selectList(null)) {
            String rawSku = s.getSku(), currency = s.getCurrency();
            if (rawSku == null || rawSku.isEmpty() || currency == null || currency.isEmpty()) continue;
            String mid = InventoryUtils.extractMiddleCode(rawSku);
            if (mid.isEmpty()) continue;
            String siteLabel = InventoryUtils.currencyToSite(currency.toUpperCase());
            if (siteLabel.isEmpty()) continue;
            LocalDate pd = s.getPaymentTime() != null ? s.getPaymentTime().toLocalDate() : null;
            if (pd == null) continue;
            int qty = s.getQuantity() != null ? s.getQuantity() : 0;
            String key = siteLabel + "|" + mid;

            if (!pd.isBefore(today.minusDays(3))) sales3d.merge(key, qty, Integer::sum);
            if (!pd.isBefore(today.minusDays(7))) sales7d.merge(key, qty, Integer::sum);
            if (!pd.isBefore(today.minusDays(30))) sales30d.merge(key, qty, Integer::sum);
            if (!pd.isBefore(today.minusDays(90))) sales90d.merge(key, qty, Integer::sum);

            String monthKey = pd.getYear() + "-" + String.format("%02d", pd.getMonthValue());
            monthlySalesMap.computeIfAbsent(key, k -> new LinkedHashMap<>())
                    .merge(monthKey, qty, Integer::sum);
        }

        // ==== 5. 品牌归属 ====
        Map<String, String> ownerByBrand = brandOwnerService.list().stream()
                .collect(Collectors.toMap(
                        e -> StringUtils.hasText(e.getBrandCode()) ? e.getBrandCode().trim().toUpperCase() : "",
                        e -> StringUtils.hasText(e.getOwnerName()) ? e.getOwnerName().trim() : "",
                        (a, b) -> a));

        // ==== 6. 出库时间 from goodcang GRN ====
        List<GoodcangGrnDetailEntity> allGrnDetails = grnDetailMapper.selectList(null);
        Set<String> allReceivingCodes = new HashSet<>();
        for (GoodcangGrnDetailEntity d : allGrnDetails) {
            if (d.getReceivingCode() != null) allReceivingCodes.add(d.getReceivingCode());
        }

        Map<String, GoodcangGrnListEntity> grnListByCode = new HashMap<>();
        if (!allReceivingCodes.isEmpty()) {
            for (GoodcangGrnListEntity gl : grnListMapper.selectList(
                    new LambdaQueryWrapper<GoodcangGrnListEntity>().in(GoodcangGrnListEntity::getReceivingCode, allReceivingCodes))) {
                grnListByCode.put(gl.getReceivingCode(), gl);
            }
        }

        Set<String> allWhCodes = new HashSet<>();
        for (GoodcangGrnListEntity gl : grnListByCode.values()) {
            if (gl.getWarehouseCode() != null) allWhCodes.add(gl.getWarehouseCode());
        }
        Map<String, GoodcangWarehouseEntity> gcWhByCode = new HashMap<>();
        if (!allWhCodes.isEmpty()) {
            for (GoodcangWarehouseEntity gw : gcWarehouseMapper.selectList(
                    new LambdaQueryWrapper<GoodcangWarehouseEntity>().in(GoodcangWarehouseEntity::getWarehouseCode, allWhCodes))) {
                gcWhByCode.put(gw.getWarehouseCode(), gw);
            }
        }

        Map<String, String> outboundTimeMap = new LinkedHashMap<>(); // "middleCode|wid" → dateStr
        for (GoodcangGrnDetailEntity d : allGrnDetails) {
            String mid = InventoryUtils.extractMiddleCode(d.getProductSku());
            if (mid.isEmpty() || d.getReceivingCode() == null) continue;
            GoodcangGrnListEntity gl = grnListByCode.get(d.getReceivingCode());
            if (gl == null || gl.getWarehouseCode() == null || gl.getCreateAt() == null) continue;
            GoodcangWarehouseEntity gw = gcWhByCode.get(gl.getWarehouseCode());
            if (gw == null || gw.getWid() == null || gw.getWid() == 0) continue;
            String key = mid + "|" + gw.getWid();
            String dt = gl.getCreateAt().toLocalDate().toString();
            String ex = outboundTimeMap.get(key);
            if (ex == null || dt.compareTo(ex) > 0) outboundTimeMap.put(key, dt);
        }

        // ==== 8. 组装结果 ====
        List<DailyPriceTrackingItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Map.Entry<String, Set<String>> entry : skuSitesMap.entrySet()) {
            String baseSku = entry.getKey();
            for (String siteLabel : entry.getValue()) {
                if (!seen.add(baseSku + "|" + siteLabel)) continue;

                DailyPriceTrackingItem item = new DailyPriceTrackingItem();
                item.setSite(siteLabel);
                item.setSku(baseSku);
                item.setProductName(skuProductNameMap.getOrDefault(baseSku, ""));

                // 销量（按 middleCode 匹配）
                String mid = InventoryUtils.extractMiddleCode(baseSku);
                String salesKey = siteLabel + "|" + mid;
                item.setLast3DaysSales(sales3d.getOrDefault(salesKey, 0));
                item.setLast7DaysSales(sales7d.getOrDefault(salesKey, 0));
                item.setLast30DaysSales(sales30d.getOrDefault(salesKey, 0));
                item.setLast90DaysSales(sales90d.getOrDefault(salesKey, 0));
                item.setMaxMonthlySales(monthlySalesMap.containsKey(salesKey)
                        ? monthlySalesMap.get(salesKey).values().stream().max(Integer::compareTo).orElse(null)
                        : null);

                // 库存
                String stockKey = baseSku + "|" + siteLabel;
                int overseasStock = overseasStockMap.getOrDefault(stockKey, 0);
                item.setOverseasWarehouseStock(overseasStock);

                // 库销比
                int d30 = item.getLast30DaysSales();
                // 库销比 = 海外仓库存 / 近30天销量 * 100（百分比），四舍五入2位小数
                BigDecimal ratio = InventoryUtils.safeDivide(overseasStock, d30);
                item.setStockSalesRatio(ratio.compareTo(BigDecimal.ZERO) > 0
                        ? ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);

                // 海外仓库龄（取该站点下所有仓库的最新出库时间 → 距今多少天）
                if (!mid.isEmpty() && !siteLabel.isEmpty()) {
                    String latestTime = null;
                    for (Map.Entry<Integer, String> e : widToSite.entrySet()) {
                        if (siteLabel.equals(e.getValue())) {
                            String t = outboundTimeMap.get(mid + "|" + e.getKey());
                            if (t != null && (latestTime == null || t.compareTo(latestTime) > 0))
                                latestTime = t;
                        }
                    }
                    if (latestTime != null) {
                        try {
                            int days = (int) java.time.temporal.ChronoUnit.DAYS.between(
                                    LocalDate.parse(latestTime), LocalDate.now());
                            item.setOverseasWarehouseAge(days);
                        } catch (Exception ignored) {}
                    }
                }

                // 预估补货量：直接从补货页采购数量匹配（按站点+去除PC前缀的SKU）

                // SKU 等级（profitRate 暂为 0，因为 last30DaysProfit 数据源未接入）
                item.setSkuLevel(InventoryUtils.calcProductLevel(d30, 0));

                // 品牌 & 操作员
                item.setBrand(InventoryUtils.extractBrandPrefix(baseSku));
                item.setOperator(InventoryUtils.matchOwner(baseSku, ownerByBrand));

                // 预留字段
                item.setOurLowestPrice(null);  // 后面批量填充
                item.setTrackingPrice(null);
                item.setTrackingProfitMargin(null);
                item.setFloorPrice(null);
                item.setReturnRate(null);
                // OE 号：从去重表获取（Step 11 批量填充）

                // eBay 售前/售后链接（从链接模板表取，{oe} 替换为实际 OE 号）
                String oe = item.getOeNumber();
                item.setEbayFrontpageUrl(linkTemplateService.buildPresaleUrl(siteLabel, oe != null ? oe : ""));
                item.setFrontpageSoldUrl(linkTemplateService.buildSoldUrl(siteLabel, oe != null ? oe : ""));
                item.setRemark("");  // 默认空，下面批量填充

                result.add(item);
            }
        }

        // ==== 9. 从补货页匹配采购数量 → 预估补货量 ====
        // 匹配键: site + "|" + stripPcPrefix(baseSku)
        // 例如每日跟价的 "2PC-BMW-30087" → 去掉PC → "BMW-30087" → 匹配补货页同站点同SKU的采购数量
        Map<String, Integer> purchaseQtyMap = new LinkedHashMap<>();
        try {
            List<com.asinking.com.openapi.dto.response.InventoryOverviewItem> overviewItems =
                    overviewService.buildOverview();
            if (overviewItems != null) {
                for (com.asinking.com.openapi.dto.response.InventoryOverviewItem ov : overviewItems) {
                    if (ov.getPurchaseQuantity() != null) {
                        String key = ov.getWarehouseNames() + "|" + ov.getSku();
                        purchaseQtyMap.put(key, ov.getPurchaseQuantity().intValue());
                    }
                }
            }
        } catch (Exception e) {
            // 补货页数据不可用时忽略
        }
        for (DailyPriceTrackingItem item : result) {
            String matchKey = item.getSite() + "|" + InventoryUtils.stripPcPrefix(item.getSku());
            Integer qty = purchaseQtyMap.get(matchKey);
            if (qty != null) {
                item.setEstimatedReplenish(qty);
            }
        }

        // ==== 10. 批量填充最低价（按 site|sku 匹配 lowest_price_record 表） ====
        Map<String, java.math.BigDecimal> lowestPriceMap = lowestPriceService.batchGetLowestPrices(
                result.stream().map(r -> r.getSite() + "|" + r.getSku()).collect(Collectors.toList()));
        for (DailyPriceTrackingItem item : result) {
            java.math.BigDecimal lp = lowestPriceMap.get(item.getSite() + "|" + item.getSku());
            if (lp != null) {
                item.setOurLowestPrice(lp);
            }
        }

        // ==== 11. 批量填充 OE 号（从去重表，并生成链接） ====
        Map<String, String> customOeMap = dedupService.batchGetOeNumbers(
                result.stream().map(r -> r.getSite() + "|" + r.getSku()).collect(Collectors.toList()));
        for (DailyPriceTrackingItem item : result) {
            String key = item.getSite() + "|" + item.getSku();
            String customOe = customOeMap.get(key);
            if (org.springframework.util.StringUtils.hasText(customOe)) {
                item.setOeNumber(customOe);
                // 用自定义 OE 重新生成链接
                item.setEbayFrontpageUrl(linkTemplateService.buildPresaleUrl(item.getSite(), customOe));
                item.setFrontpageSoldUrl(linkTemplateService.buildSoldUrl(item.getSite(), customOe));
            }
        }

        // ==== 12. 兜底：确保每行都有链接（OE为空也用空字符串替换{oe}） ====
        for (DailyPriceTrackingItem item : result) {
            if (item.getEbayFrontpageUrl() == null) {
                String oe = item.getOeNumber();
                item.setEbayFrontpageUrl(linkTemplateService.buildPresaleUrl(item.getSite(), oe != null ? oe : ""));
            }
            if (item.getFrontpageSoldUrl() == null) {
                String oe = item.getOeNumber();
                item.setFrontpageSoldUrl(linkTemplateService.buildSoldUrl(item.getSite(), oe != null ? oe : ""));
            }
        }

        // ==== 13. 跟卖价格/利润率/底线价 = ebay_product_dedup（按 site|sku 匹配） ====
        Map<String, java.math.BigDecimal> tpMap = dedupService.batchGetTrackingPrices();
        Map<String, java.math.BigDecimal> tmMap = dedupService.batchGetTrackingProfitMargins();
        Map<String, java.math.BigDecimal> fpMap = dedupService.batchGetFloorPrices();
        for (DailyPriceTrackingItem item : result) {
            String key = item.getSite() + "|" + item.getSku();
            java.math.BigDecimal tp = tpMap.get(key);
            if (tp != null) item.setTrackingPrice(tp);
            java.math.BigDecimal tm = tmMap.get(key);
            if (tm != null) item.setTrackingProfitMargin(tm);
            java.math.BigDecimal fp = fpMap.get(key);
            if (fp != null) item.setFloorPrice(fp);
        }

        // ==== 14. 批量填充备注（从 ebay_product_dedup 表） ====
        if (!result.isEmpty()) {
            Map<String, String> remarkMap = dedupService.batchGetRemarks(
                    result.stream().map(r -> r.getSite() + "|" + r.getSku()).collect(Collectors.toList()));
            for (DailyPriceTrackingItem item : result) {
                String key = item.getSite() + "|" + item.getSku();
                String savedRemark = remarkMap.get(key);
                if (savedRemark != null && !savedRemark.isEmpty()) {
                    item.setRemark(savedRemark);
                }
            }
        }

        return result;
    }

    // ====================================================================
    // 过滤/分页工具
    // ====================================================================

    /** 仓库 ID 配置解析 */
    private List<Integer> parseInventoryWids() {
        String r = lingxingProperties.getInventoryWids();
        List<Integer> l = new ArrayList<>();
        if (StringUtils.hasText(r)) {
            for (String p : r.split(",")) {
                try { l.add(Integer.parseInt(p.trim())); }
                catch (NumberFormatException ignored) {}
            }
        }
        return l;
    }

    /** 筛选：空值全部通过；逗号分隔多选则任一匹配即可，否则精确匹配 */
    private boolean matchMulti(String filter, String actual) {
        if (filter == null || filter.trim().isEmpty()) return true;
        if (filter.contains(",")) {
            return Arrays.stream(filter.split(","))
                    .map(String::trim)
                    .anyMatch(s -> s.equals(actual));
        }
        return filter.trim().equals(actual);
    }

    /** 模糊包含匹配 */
    private boolean matchContains(String keyword, String actual) {
        if (keyword == null || keyword.trim().isEmpty()) return true;
        if (actual == null) return false;
        return actual.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    /** 重算并写入数据库 */
    public void refreshTable() {
        LOG.info("==== 每日跟价数据重算写入数据库 开始 ====");
        long t = System.currentTimeMillis();
        try {
            List<DailyPriceTrackingItem> items = computeDailyPriceTracking();
            cacheMapper.delete(null);
            for (DailyPriceTrackingItem item : items) cacheMapper.insert(dtoToEntity(item));
            LOG.info("==== 重算完成: {} 条 耗时{}ms ====", items.size(), System.currentTimeMillis() - t);
        } catch (Exception e) { LOG.error("重算失败", e); }
    }

    private DailyPriceTrackingCacheEntity dtoToEntity(DailyPriceTrackingItem i) {
        DailyPriceTrackingCacheEntity e = new DailyPriceTrackingCacheEntity();
        e.setSite(i.getSite()); e.setSku(i.getSku()); e.setProductName(i.getProductName());
        e.setSkuLevel(i.getSkuLevel()); e.setLast3DaysSales(i.getLast3DaysSales());
        e.setLast7DaysSales(i.getLast7DaysSales()); e.setLast30DaysSales(i.getLast30DaysSales());
        e.setLast90DaysSales(i.getLast90DaysSales()); e.setMaxMonthlySales(i.getMaxMonthlySales());
        e.setOverseasWarehouseStock(i.getOverseasWarehouseStock()); e.setOverseasWarehouseAge(i.getOverseasWarehouseAge());
        e.setStockSalesRatio(i.getStockSalesRatio()); e.setEstimatedReplenish(i.getEstimatedReplenish());
        e.setOurLowestPrice(i.getOurLowestPrice()); e.setTrackingPrice(i.getTrackingPrice());
        e.setTrackingProfitMargin(i.getTrackingProfitMargin()); e.setFloorPrice(i.getFloorPrice());
        e.setReturnRate(i.getReturnRate()); e.setEbayFrontpageUrl(i.getEbayFrontpageUrl());
        e.setFrontpageSoldUrl(i.getFrontpageSoldUrl()); e.setBrand(i.getBrand()); e.setOperator(i.getOperator());
        e.setRemark(i.getRemark()); e.setOeNumber(i.getOeNumber()); return e;
    }

    private DailyPriceTrackingItem entityToDto(DailyPriceTrackingCacheEntity e) {
        DailyPriceTrackingItem i = new DailyPriceTrackingItem();
        i.setSite(e.getSite()); i.setSku(e.getSku()); i.setProductName(e.getProductName());
        i.setSkuLevel(e.getSkuLevel()); i.setLast3DaysSales(e.getLast3DaysSales());
        i.setLast7DaysSales(e.getLast7DaysSales()); i.setLast30DaysSales(e.getLast30DaysSales());
        i.setLast90DaysSales(e.getLast90DaysSales()); i.setMaxMonthlySales(e.getMaxMonthlySales());
        i.setOverseasWarehouseStock(e.getOverseasWarehouseStock()); i.setOverseasWarehouseAge(e.getOverseasWarehouseAge());
        i.setStockSalesRatio(e.getStockSalesRatio()); i.setEstimatedReplenish(e.getEstimatedReplenish());
        i.setOurLowestPrice(e.getOurLowestPrice()); i.setTrackingPrice(e.getTrackingPrice());
        i.setTrackingProfitMargin(e.getTrackingProfitMargin()); i.setFloorPrice(e.getFloorPrice());
        i.setReturnRate(e.getReturnRate()); i.setEbayFrontpageUrl(e.getEbayFrontpageUrl());
        i.setFrontpageSoldUrl(e.getFrontpageSoldUrl()); i.setBrand(e.getBrand()); i.setOperator(e.getOperator());
        i.setRemark(e.getRemark()); i.setOeNumber(e.getOeNumber()); return i;
    }

}
