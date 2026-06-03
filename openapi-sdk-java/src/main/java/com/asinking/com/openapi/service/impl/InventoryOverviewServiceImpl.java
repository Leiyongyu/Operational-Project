package com.asinking.com.openapi.service.impl;

import com.alibaba.fastjson.JSON;
import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.dto.response.InventoryOverviewItem;
import com.asinking.com.openapi.dto.response.WarehouseOptionItem;
import com.asinking.com.openapi.entity.*;
import com.asinking.com.openapi.mapper.mp.*;
import com.asinking.com.openapi.service.*;
import com.asinking.com.openapi.utils.InventoryUtils;
import com.asinking.com.openapi.entity.EbayProductDedupEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 库存总览服务实现：按 SKU+站点维度聚合库存、销量、库销比、采购建议，
 * 支持 admin 全量 / 普通用户按品牌负责人过滤，结果写入快照表并缓存30分钟。
 */
@Service
public class InventoryOverviewServiceImpl implements InventoryOverviewService {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryOverviewServiceImpl.class);
    private final LingxingProperties properties;
    private final WarehouseService warehouseService;
    private final WarehouseInventoryDetailService inventoryService;
    private final BrandOwnerService brandOwnerService;
    private final UserService userService;
    private final GoodcangGrnListMapper grnListMapper;
    private final GoodcangGrnDetailMapper grnDetailMapper;
    private final GoodcangWarehouseMapper gcWarehouseMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final WarehouseStatementMapper warehouseStatementMapper;
    private final EbaySalesMapper ebaySalesMapper;
    private final PurchasePlanMapper purchasePlanMapper;
    private final InventorySnapshotMapper snapshotMapper;
    private final EbayProductDedupService dedupService;

    /** 快照缓存：30 分钟自动过期，同步任务跑完后主动清除 */
    private final Cache<String, List<InventoryOverviewItem>> overviewCache = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();
    private static final String CACHE_KEY = "overview";

    public InventoryOverviewServiceImpl(LingxingProperties properties, WarehouseService warehouseService,
            WarehouseInventoryDetailService inventoryService, BrandOwnerService brandOwnerService,
            UserService userService,
            GoodcangGrnListMapper grnListMapper,
            GoodcangGrnDetailMapper grnDetailMapper, GoodcangWarehouseMapper gcWarehouseMapper,
            PurchaseOrderMapper purchaseOrderMapper, WarehouseStatementMapper warehouseStatementMapper,
            EbaySalesMapper ebaySalesMapper, PurchasePlanMapper purchasePlanMapper,
            InventorySnapshotMapper snapshotMapper,
            EbayProductDedupService dedupService) {
        this.properties = properties; this.warehouseService = warehouseService;
        this.inventoryService = inventoryService; this.brandOwnerService = brandOwnerService;
        this.userService = userService;
        this.grnListMapper = grnListMapper;
        this.grnDetailMapper = grnDetailMapper; this.gcWarehouseMapper = gcWarehouseMapper;
        this.purchaseOrderMapper = purchaseOrderMapper; this.warehouseStatementMapper = warehouseStatementMapper;
        this.ebaySalesMapper = ebaySalesMapper; this.purchasePlanMapper = purchasePlanMapper;
        this.snapshotMapper = snapshotMapper;
        this.dedupService = dedupService;
    }

    // ====================================================================
    // 公开 API
    // ====================================================================

    @Override
    public List<InventoryOverviewItem> buildOverview() {
        // 1) 优先从 Guava 内存缓存取
        List<InventoryOverviewItem> cached = overviewCache.getIfPresent(CACHE_KEY);
        if (cached != null) return cached;

        // 2) 从快照表取（表不存在则忽略，降级实时计算）
        try {
            InventorySnapshotEntity snap = snapshotMapper.selectById(1);
            if (snap != null && StringUtils.hasText(snap.getDataJson())) {
                try {
                    List<InventoryOverviewItem> items = JSON.parseArray(snap.getDataJson(), InventoryOverviewItem.class);
                    if (items != null && !items.isEmpty()) {
                        overviewCache.put(CACHE_KEY, items);
                        return items;
                    }
                } catch (Exception e) {
                    LOG.warn("快照 JSON 解析失败，降级实时计算", e);
                }
            }
        } catch (Exception e) {
            LOG.warn("快照表不可用（可能未建表），降级实时计算: {}", e.getMessage());
        }

        // 3) 降级：缓存和快照都空，首次启动实时算一次
        List<InventoryOverviewItem> result = computeOverview();
        overviewCache.put(CACHE_KEY, result);
        return result;
    }

    @Override
    public void refreshSnapshot() {
        LOG.info("==== 运营数据快照刷新 开始 ====");
        long t = System.currentTimeMillis();
        try {
            List<InventoryOverviewItem> items = computeOverview();
            String json = JSON.toJSONString(items);

            InventorySnapshotEntity snap = snapshotMapper.selectById(1);
            if (snap == null) {
                snap = new InventorySnapshotEntity();
                snap.setId(1);
                snap.setDataJson(json);
                snapshotMapper.insert(snap);
            } else {
                snap.setDataJson(json);
                snapshotMapper.updateById(snap);
            }

            // 清除缓存让下次请求读新快照
            overviewCache.invalidate(CACHE_KEY);
            overviewCache.put(CACHE_KEY, items);

            LOG.info("==== 运营数据快照刷新 完成: {} 条 耗时{}ms ====", items.size(), System.currentTimeMillis() - t);
        } catch (Exception e) {
            LOG.error("快照刷新失败", e);
        }
    }

    @Override
    public List<InventoryOverviewItem> filterOverview(String sku, String warehouse, String userId, String role) {
        List<InventoryOverviewItem> all = buildOverview();
        String kw = StringUtils.hasText(sku) ? sku.trim().toLowerCase() : null;

        // 支持逗号分隔的多仓库标签筛选（如 "美国,德国"）
        Set<String> warehouseLabels = Collections.emptySet();
        if (StringUtils.hasText(warehouse)) {
            warehouseLabels = Arrays.stream(warehouse.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
        }

        Set<String> brands = null;
        if (!"admin".equalsIgnoreCase(role != null ? role.trim() : "") && StringUtils.hasText(userId))
            brands = loadUserBrandCodes(userId);
        final Set<String> fb = brands;
        final Set<String> whLabels = warehouseLabels;
        return all.stream().filter(item -> {
            if (kw != null && (item.getSku() == null || !item.getSku().toLowerCase().contains(kw))) return false;
            if (!whLabels.isEmpty() && !whLabels.contains(item.getWarehouseNames())) return false;
            if (fb != null && !fb.isEmpty() && !matchesUserBrand(item.getSku(), fb)) return false;
            return true;
        }).collect(Collectors.toList());
    }

    @Override
    public List<WarehouseOptionItem> getWarehouseOptions() {
        List<Integer> wids = parseInventoryWids();
        if (wids.isEmpty()) return Collections.emptyList();
        Map<String, List<Integer>> g = new LinkedHashMap<>();
        for (WarehouseEntity wh : warehouseService.lambdaQuery().in(WarehouseEntity::getWid, wids).list())
            g.computeIfAbsent(toWarehouseLabel(wh), k -> new ArrayList<>()).add(wh.getWid());
        return g.entrySet().stream().map(e -> new WarehouseOptionItem(e.getKey(), e.getValue().stream().map(String::valueOf).collect(Collectors.joining(",")))).collect(Collectors.toList());
    }

    // ====================================================================
    // 核心计算逻辑（仅在刷新快照时调用）
    // ====================================================================

    private List<InventoryOverviewItem> computeOverview() {
        List<Integer> inventoryWids = parseInventoryWids();

        // ==== 仓库信息（只查一次，复用到库存、出库、站点映射）====
        Map<Integer, WarehouseEntity> warehouseMap = warehouseService.lambdaQuery()
                .in(WarehouseEntity::getWid, inventoryWids)
                .ne(WarehouseEntity::getWid, 1194)
                .list().stream()
                .collect(Collectors.toMap(WarehouseEntity::getWid, e -> e, (a, b) -> a));

        Map<Integer, String> widToSite = new HashMap<>();
        for (Map.Entry<Integer, WarehouseEntity> e : warehouseMap.entrySet())
            widToSite.put(e.getKey(), toWarehouseLabel(e.getValue()));

        // ==== 1. 基准 (groupKey, site) from ebay_product_dedup（已去重） ====
        // 使用 extractInventoryGroupKey：去掉 PC 前缀后提取 baseSku，
        // 使 2PC-BMW-30087 和 BMW-30087 归入同一商品分组
        Map<String, String> skuProductNameMap = new LinkedHashMap<>();
        Map<String, Map<String, SkuSiteInv>> siteRowsBySku = new LinkedHashMap<>();
        for (EbayProductDedupEntity dedup : dedupService.listAll()) {
            String rawSku = dedup.getSku() != null ? dedup.getSku().trim() : "";
            if (rawSku.isEmpty()) continue;
            String groupKey = InventoryUtils.extractInventoryGroupKey(rawSku);
            if (groupKey.isEmpty()) continue;
            if (!skuProductNameMap.containsKey(groupKey) && dedup.getProductName() != null)
                skuProductNameMap.put(groupKey, dedup.getProductName().trim());
            String site = dedup.getSite();  // 已归一化
            if (site == null || site.isEmpty()) continue;
            siteRowsBySku.computeIfAbsent(groupKey, k -> new LinkedHashMap<>())
                    .put(site, new SkuSiteInv(groupKey, site));
        }

        // ==== 2. 库存（使用 groupKey 归并 PC/非PC）====
        for (WarehouseInventoryDetailEntity d : inventoryService.lambdaQuery()
                .in(WarehouseInventoryDetailEntity::getWid, inventoryWids).list()) {
            String baseSku = InventoryUtils.extractInventoryGroupKey(d.getSku());
            if (baseSku.isEmpty()) continue;
            WarehouseEntity wh = warehouseMap.get(d.getWid());
            if (wh == null) continue;
            String label = toWarehouseLabel(wh);
            if (label.isEmpty()) continue;
            // 只汇总已有 eBay 刊登的 SKU，不新建
            Map<String, SkuSiteInv> sm = siteRowsBySku.get(baseSku);
            if (sm == null) continue;
            SkuSiteInv inv = sm.get(label);
            if (inv == null) {
                inv = new SkuSiteInv(baseSku, label);
                sm.put(label, inv);
            }
            int s = d.getProductValidNum() != null ? d.getProductValidNum() : 0;
            int o = d.getProductOnway() != null ? d.getProductOnway() : 0;
            // 成都在途使用 quantity_receive 替代 product_onway
            int qr = d.getQuantityReceive() != null ? d.getQuantityReceive().intValue() : 0;
            inv.lockNum += d.getProductLockNum() != null ? d.getProductLockNum() : 0;
            if (wh.getType() != null && wh.getType() == 3) { inv.overseasSellable += s; inv.overseasOnway += o; }
            else { inv.localSellable += s; inv.localOnway += qr; }
        }

        // ==== 3. 谷仓出库时间（已消除 N+1：批量查 GRN list + warehouse）====
        List<GoodcangGrnDetailEntity> allGrnDetails = grnDetailMapper.selectList(null);
        Set<String> allReceivingCodes = new HashSet<>();
        for (GoodcangGrnDetailEntity d : allGrnDetails)
            if (d.getReceivingCode() != null) allReceivingCodes.add(d.getReceivingCode());

        // 批量查 grn_list，按 receiving_code 建索引
        Map<String, GoodcangGrnListEntity> grnListByCode = new HashMap<>();
        if (!allReceivingCodes.isEmpty())
            for (GoodcangGrnListEntity gl : grnListMapper.selectList(
                    new LambdaQueryWrapper<GoodcangGrnListEntity>().in(GoodcangGrnListEntity::getReceivingCode, allReceivingCodes)))
                grnListByCode.put(gl.getReceivingCode(), gl);

        // 批量查谷仓仓库，按 warehouse_code 建索引
        Set<String> allWhCodes = new HashSet<>();
        for (GoodcangGrnListEntity gl : grnListByCode.values())
            if (gl.getWarehouseCode() != null) allWhCodes.add(gl.getWarehouseCode());
        Map<String, GoodcangWarehouseEntity> gcWhByCode = new HashMap<>();
        if (!allWhCodes.isEmpty())
            for (GoodcangWarehouseEntity gw : gcWarehouseMapper.selectList(
                    new LambdaQueryWrapper<GoodcangWarehouseEntity>().in(GoodcangWarehouseEntity::getWarehouseCode, allWhCodes)))
                gcWhByCode.put(gw.getWarehouseCode(), gw);

        Map<String, String> createTimeMap = new LinkedHashMap<>();
        for (GoodcangGrnDetailEntity d : allGrnDetails) {
            String mid = InventoryUtils.extractMiddleCodeForInventory(d.getProductSku());
            if (mid.isEmpty() || d.getReceivingCode() == null) continue;
            GoodcangGrnListEntity gl = grnListByCode.get(d.getReceivingCode());
            if (gl == null || gl.getWarehouseCode() == null || gl.getCreateAt() == null) continue;
            GoodcangWarehouseEntity gw = gcWhByCode.get(gl.getWarehouseCode());
            if (gw == null || gw.getWid() == null || gw.getWid() == 0) continue;
            String key = mid + "|" + gw.getWid(), dt = gl.getCreateAt().toLocalDate().toString();
            String ex = createTimeMap.get(key);
            if (ex == null || dt.compareTo(ex) > 0) createTimeMap.put(key, dt);
        }

        // ==== 4. 采购周期：purchase_order 只查一次，同时用于采购周期和待交付 ====
        List<PurchaseOrderEntity> allPurchaseOrders = purchaseOrderMapper.selectList(null);
        Map<String, LocalDate> orderTimeMap = new LinkedHashMap<>();
        Map<String, Integer> purchasePendingMap = new LinkedHashMap<>();

        for (PurchaseOrderEntity po : allPurchaseOrders) {
            String sku = po.getItemSku(), whName = po.getWareHouseName();
            if (sku == null || sku.trim().isEmpty() || whName == null || whName.trim().isEmpty()) continue;
            String site = whNameToSite(whName.trim());
            if (site.isEmpty()) continue;
            String key = site + "|" + InventoryUtils.extractInventoryGroupKey(sku.trim());

            // 采购下单时间（取最新）
            if (po.getOrderTime() != null) {
                LocalDate od = po.getOrderTime().toLocalDate(), ex = orderTimeMap.get(key);
                if (ex == null || od.isAfter(ex)) orderTimeMap.put(key, od);
            }

            // 采购待交付（待审批/待下单 的数量）
            String statusText = po.getStatusText();
            if ("待审批".equals(statusText) || "待下单".equals(statusText))
                purchasePendingMap.merge(key, 1, Integer::sum);
        }

        // ==== 5. 入库时间 ====
        Map<String, LocalDate> inboundTimeMap = new LinkedHashMap<>();
        for (WarehouseStatementEntity ws : warehouseStatementMapper.selectList(
                new LambdaQueryWrapper<WarehouseStatementEntity>().eq(WarehouseStatementEntity::getType, 22))) {
            String sku = ws.getSku(), whName = ws.getWareHouseName();
            if (sku == null || sku.trim().isEmpty() || whName == null || whName.trim().isEmpty() || ws.getOptTime() == null) continue;
            String site = whNameToSite(whName.trim());
            if (site.isEmpty()) continue;
            String key = site + "|" + InventoryUtils.extractInventoryGroupKey(sku.trim());
            LocalDate od = ws.getOptTime().toLocalDate(), ex = inboundTimeMap.get(key);
            if (ex == null || od.isBefore(ex)) inboundTimeMap.put(key, od);
        }

        // 计算采购周期天数
        Map<String, Integer> purchaseCycleMap = new LinkedHashMap<>();
        for (String k : orderTimeMap.keySet()) {
            LocalDate od = orderTimeMap.get(k), ib = inboundTimeMap.get(k);
            if (od != null && ib != null && !ib.isBefore(od))
                purchaseCycleMap.put(k, (int) java.time.temporal.ChronoUnit.DAYS.between(od, ib));
        }

        // ==== 6. eBay 销量 ====
        Map<String, Integer> sales7d = new LinkedHashMap<>(), sales30d = new LinkedHashMap<>(), sales90d = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> monthlySalesMap = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (EbaySalesEntity s : ebaySalesMapper.selectList(null)) {
            String sku = s.getSku(), currency = s.getCurrency();
            if (sku == null || sku.isEmpty() || currency == null || currency.isEmpty()) continue;
            String mid = InventoryUtils.extractMiddleCodeForInventory(sku);
            if (mid.isEmpty()) continue;
            String site = currencyToSite(currency.toUpperCase());
            if (site.isEmpty()) continue;
            LocalDate pd = s.getPaymentTime() != null ? s.getPaymentTime().toLocalDate() : null;
            if (pd == null) continue;
            int qty = s.getQuantity() != null ? s.getQuantity() : 0;
            String key = site + "|" + mid;

            if (!pd.isBefore(today.minusDays(7))) sales7d.merge(key, qty, Integer::sum);
            if (!pd.isBefore(today.minusDays(30))) sales30d.merge(key, qty, Integer::sum);
            if (!pd.isBefore(today.minusDays(90))) sales90d.merge(key, qty, Integer::sum);

            String monthKey = pd.getYear() + "-" + String.format("%02d", pd.getMonthValue());
            monthlySalesMap.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(monthKey, qty, Integer::sum);
        }

        // ==== 7. 品牌归属 ====
        Map<String, String> ownerByBrand = brandOwnerService.list().stream().collect(Collectors.toMap(
                e -> StringUtils.hasText(e.getBrandCode()) ? e.getBrandCode().trim().toUpperCase() : "",
                e -> StringUtils.hasText(e.getOwnerName()) ? e.getOwnerName().trim() : "", (a, b) -> a));

        // ==== 8. 采购计划（status_text=待审批，按站点+baseSku汇总数量）====
        Map<String, Integer> purchasePlanCountMap = new LinkedHashMap<>();
        for (PurchasePlanEntity pp : purchasePlanMapper.selectList(
                new LambdaQueryWrapper<PurchasePlanEntity>().eq(PurchasePlanEntity::getStatusText, "待审批"))) {
            String sku = pp.getSku(), whName = pp.getWarehouseName();
            if (sku == null || sku.trim().isEmpty() || whName == null || whName.trim().isEmpty()) continue;
            String site = whNameToSite(whName.trim());
            if (site.isEmpty()) continue;
            String key = site + "|" + InventoryUtils.extractInventoryGroupKey(sku.trim());
            purchasePlanCountMap.merge(key, 1, Integer::sum);
        }

        // ==== 9. 构建结果 ====
        List<InventoryOverviewItem> result = new ArrayList<>();
        Set<String> fs = new HashSet<>();
        for (Map.Entry<String, Map<String, SkuSiteInv>> se : siteRowsBySku.entrySet()) {
            String baseSku = se.getKey();
            for (SkuSiteInv inv : se.getValue().values()) {
                if (!fs.add(baseSku + "|" + inv.siteLabel)) continue;
                InventoryOverviewItem item = new InventoryOverviewItem();
                item.setSku(baseSku);
                item.setProductName(skuProductNameMap.getOrDefault(baseSku, ""));
                item.setWarehouseNames(inv.siteLabel);
                item.setOverseasOnway(inv.overseasOnway); item.setOverseasSellable(inv.overseasSellable);
                item.setOverseasTotal(inv.overseasSellable + inv.overseasOnway);
                item.setLocalOnway(inv.localOnway); item.setLocalSellable(inv.localSellable);
                item.setLockNum(inv.lockNum);
                item.setTotalInventory(inv.overseasSellable + inv.overseasOnway + inv.localSellable + inv.localOnway);
                String salesKey = inv.siteLabel + "|" + InventoryUtils.extractMiddleCode(baseSku);
                item.setLast7DaysSales(sales7d.getOrDefault(salesKey, 0));
                item.setLast30DaysSales(sales30d.getOrDefault(salesKey, 0));
                item.setLast90DaysSales(sales90d.getOrDefault(salesKey, 0));
                item.setMaxMonthlySales(monthlySalesMap.containsKey(salesKey)
                        ? monthlySalesMap.get(salesKey).values().stream().max(Integer::compareTo).orElse(0)
                        : null);

                String mid = InventoryUtils.extractMiddleCode(baseSku);
                if (!mid.isEmpty() && !inv.siteLabel.isEmpty()) {
                    String latestTime = null;
                    for (Map.Entry<Integer, String> e : widToSite.entrySet())
                        if (inv.siteLabel.equals(e.getValue())) {
                            String t = createTimeMap.get(mid + "|" + e.getKey());
                            if (t != null && (latestTime == null || t.compareTo(latestTime) > 0))
                                latestTime = t;
                        }
                    if (latestTime != null) {
                        item.setLastLocalOutboundTime(latestTime);
                        try { item.setOutboundDays((int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(latestTime), LocalDate.now())); } catch (Exception ignored) {}
                    }
                }

                Integer pc = purchaseCycleMap.get(inv.siteLabel + "|" + baseSku);
                if (pc != null) item.setPurchaseCycle(pc);

                item.setPurchasePendingDelivery(purchasePendingMap.getOrDefault(inv.siteLabel + "|" + baseSku, 0));
                item.setPurchasePlan(String.valueOf(purchasePlanCountMap.getOrDefault(inv.siteLabel + "|" + baseSku, 0)));

                Integer cycle = item.getPurchaseCycle(), od = item.getOutboundDays();
                if (cycle != null && od != null) {
                    BigDecimal avg = BigDecimal.valueOf(item.getLast90DaysSales()).divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
                    // 采购数量 = 近3月均销量 × (采购周期 + 出库天数) / 30
                    item.setPurchaseQuantity(avg.multiply(BigDecimal.valueOf((cycle + od) / 30.0))
                            .setScale(0, RoundingMode.HALF_UP));
                }

                Integer mm = item.getMaxMonthlySales();
                if (mm != null && mm > 0) {
                    item.setMaxMonthlyReplenish((int) Math.max(0, Math.round(mm * 4.03 - item.getTotalInventory())));
                }

                int d30 = item.getLast30DaysSales();
                item.setOverseasInStockRatio(divide(item.getOverseasSellable(), d30));
                item.setOverseasTotalRatio(divide(item.getOverseasTotal(), d30));
                item.setTotalInventoryRatio(divide(item.getTotalInventory(), d30));
                item.setOwner(matchOwner(baseSku, ownerByBrand));
                // 计算 SKU 产品等级（按 站点+SKU 维度，和采购计划备注逻辑一致）
                item.setSkuLevel(calcProductLevel(d30,
                        item.getLast30DaysProfit() != null ? item.getLast30DaysProfit().doubleValue() : 0));
                result.add(item);
            }
        }
        return result;
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    private Set<String> loadUserBrandCodes(String uid) {
        UserEntity u = userService.getById(uid);
        if (u == null || !StringUtils.hasText(u.getOwnerName())) return Collections.emptySet();
        Set<String> s = new HashSet<>();
        for (BrandOwnerEntity bo : brandOwnerService.lambdaQuery().eq(BrandOwnerEntity::getOwnerName, u.getOwnerName().trim()).list())
            if (StringUtils.hasText(bo.getBrandCode())) s.add(bo.getBrandCode().trim().toUpperCase());
        return s;
    }
    private boolean matchesUserBrand(String sku, Set<String> brands) {
        if (!StringUtils.hasText(sku)) return false;
        int i = sku.indexOf('-');
        return brands.contains(i > 0 ? sku.substring(0, i).toUpperCase() : sku.toUpperCase());
    }
    private List<Integer> parseInventoryWids() {
        String r = properties.getInventoryWids(); List<Integer> l = new ArrayList<>();
        if (StringUtils.hasText(r)) for (String p : r.split(",")) try { l.add(Integer.parseInt(p.trim())); } catch (NumberFormatException ignored) {}
        return l;
    }
    private BigDecimal divide(int n, int d) {
        return InventoryUtils.safeDivide(n, d);
    }
    private String matchOwner(String sku, Map<String, String> ob) {
        return InventoryUtils.matchOwner(sku, ob);
    }
    private String extractMiddleCode(String sku) {
        return InventoryUtils.extractMiddleCode(sku);
    }
    private String extractBaseSku(String sku) {
        return InventoryUtils.extractBaseSku(sku);
    }
    private String mapSiteName(String n) { return InventoryUtils.mapSiteName(n); }

    private String currencyToSite(String c) { return InventoryUtils.currencyToSite(c); }

    private String whNameToSite(String n) {
        return InventoryUtils.whNameToSite(n);
    }
    private String toWarehouseLabel(WarehouseEntity wh) { return whNameToSite(wh.getName()); }

    /** SKU 产品等级：委托 InventoryUtils */
    private String calcProductLevel(int sales, double profitRate) {
        return InventoryUtils.calcProductLevel(sales, profitRate);
    }

    private static class SkuSiteInv { String sku, siteLabel; int overseasSellable, overseasOnway, localSellable, localOnway, lockNum; SkuSiteInv(String s, String l) { sku = s; siteLabel = l; } }
}
