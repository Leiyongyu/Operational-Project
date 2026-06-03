package com.asinking.com.openapi.utils;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 库存/运营数据共享工具类：SKU 解析、站点映射、品牌归属、产品等级计算等。
 * 同时被 InventoryOverviewServiceImpl 和 DailyPriceTrackingServiceImpl 使用。
 */
public final class InventoryUtils {

    private InventoryUtils() {}

    // ====================================================================
    // 站点名称映射
    // ====================================================================

    private static final Map<String, String> SITE_NAME_MAP = new HashMap<>();
    static {
        SITE_NAME_MAP.put("ebay汽配", "美国");
    }

    private static final Map<String, String> CURRENCY_TO_SITE = new HashMap<>();
    static {
        CURRENCY_TO_SITE.put("USD", "美国");
        CURRENCY_TO_SITE.put("GBP", "英国");
        CURRENCY_TO_SITE.put("EUR", "德国");
    }

    // ====================================================================
    // SKU 解析
    // ====================================================================

    /**
     * 提取基础 SKU（前两段），"数字PC" 格式保留前三段。
     * 例： "MCD-20018-BLACK" → "MCD-20018"
     *      "2PC-ABC-XYZ"     → "2PC-ABC-XYZ"
     */
    public static String extractBaseSku(String sku) {
        if (!StringUtils.hasText(sku)) return "";
        String[] parts = sku.split("-");
        if (parts.length < 2) return sku;
        if (parts[0].matches("\\d+PC")) {
            if (parts.length >= 3) return parts[0] + "-" + parts[1] + "-" + parts[2];
            return parts[0] + "-" + parts[1];
        }
        return parts[0] + "-" + parts[1];
    }

    /**
     * 提取中间码（第二段）。例： "MCD-20018-BLACK" → "20018"
     */
    public static String extractMiddleCode(String sku) {
        if (!StringUtils.hasText(sku)) return "";
        String[] parts = sku.split("-");
        return parts.length >= 2 ? parts[1] : "";
    }

    /**
     * 提取品牌前缀（第一段，大写）。带 PC 前缀时取 PC 后面的段。
     * 例： "MCD-20018"      → "MCD"
     *      "2PC-BMW-30087"   → "BMW"
     *      "BMW-30087"       → "BMW"
     */
    public static String extractBrandPrefix(String sku) {
        if (!StringUtils.hasText(sku)) return "";
        // 去掉 "数字PC-" 前缀，使 2PC-BMW-30087 的品牌识别为 BMW 而非 2PC
        String effective = stripPcPrefix(sku);
        int i = effective.indexOf('-');
        return i > 0 ? effective.substring(0, i).toUpperCase() : effective.toUpperCase();
    }

    /**
     * 去掉 SKU 开头的 "数字PC-" 前缀（如果存在）。
     * 用于补货页将 2PC-BMW-30087 和 BMW-30087 视为同一商品。
     * 例： "2PC-BMW-30087" → "BMW-30087"
     *      "BMW-30087"     → "BMW-30087"（不变）
     */
    public static String stripPcPrefix(String sku) {
        if (!StringUtils.hasText(sku)) return "";
        if (sku.matches("\\d+PC-.*")) {
            int firstDash = sku.indexOf('-');
            return firstDash > 0 ? sku.substring(firstDash + 1) : sku;
        }
        return sku;
    }

    /**
     * 补货页 SKU 分组键：先去掉 PC 前缀，再提取 baseSku。
     * 确保 2PC-BMW-30087 和 BMW-30087 归入同一商品分组。
     * 例： "2PC-BMW-30087-BLACK" → "BMW-30087"
     *      "BMW-30087-BLACK"     → "BMW-30087"
     */
    public static String extractInventoryGroupKey(String sku) {
        return extractBaseSku(stripPcPrefix(sku));
    }

    /**
     * 补货页中间码提取：先去掉 PC 前缀，再提取第二段。
     * 用于销量和出库时间的分组匹配。
     * 例： "2PC-BMW-30087-BLACK" → "30087"
     *      "BMW-30087-BLACK"     → "30087"
     */
    public static String extractMiddleCodeForInventory(String sku) {
        return extractMiddleCode(stripPcPrefix(sku));
    }

    // ====================================================================
    // 站点/仓库标签映射
    // ====================================================================

    /** 刊登站点名 → 站点标签（忽略大小写匹配） */
    public static String mapSiteName(String siteName) {
        String t = siteName != null ? siteName.trim() : "";
        if (!StringUtils.hasText(t)) return "";
        // 大小写不敏感匹配（eBay汽配 / ebay汽配 / EBAY汽配 → 美国）
        for (Map.Entry<String, String> e : SITE_NAME_MAP.entrySet()) {
            if (e.getKey().equalsIgnoreCase(t)) return e.getValue();
        }
        return t;
    }

    /** 币种 → 站点标签 */
    public static String currencyToSite(String currency) {
        return CURRENCY_TO_SITE.getOrDefault(currency, "");
    }

    /**
     * 仓库名称 → 站点标签。
     * CTUAMZ 开头的成都仓返回空（不参与海外站点统计）。
     */
    public static String whNameToSite(String warehouseName) {
        if (!StringUtils.hasText(warehouseName)) return "";
        if (warehouseName.startsWith("CTUAMZ")) return "";
        if (warehouseName.contains("-US") || warehouseName.contains("加州") || warehouseName.contains("新泽西"))
            return "美国";
        if (warehouseName.contains("-DE") || warehouseName.contains("德国")) return "德国";
        if (warehouseName.contains("-UK") || warehouseName.contains("英国")) return "英国";
        return "";
    }

    // ====================================================================
    // eBay 售前/售后链接
    // ====================================================================

    /** 根据站点生成 eBay 售前链接 */
    public static String ebayPresaleUrl(String site, String sku) {
        String base = ebayBaseUrl(site);
        if (base == null) return null;
        String sop = "美国".equals(site) ? "&_fcid=1&_sop=15" : "&_sop=15";
        return base + "/sch/i.html?_nkw=" + sku + "&_sacat=0&_from=R40" + sop;
    }

    /** 根据站点生成 eBay 售后（已售）链接 */
    public static String ebaySoldUrl(String site, String sku) {
        String base = ebayBaseUrl(site);
        if (base == null) return null;
        String extra = "德国".equals(site) ? "&_sop=10" : "&_fcid=1";
        return base + "/sch/i.html?_nkw=" + sku
                + "&_sacat=0&_from=R40&LH_Complete=1&rt=nc&LH_Sold=1" + extra;
    }

    private static String ebayBaseUrl(String site) {
        if ("美国".equals(site)) return "https://www.ebay.com";
        if ("英国".equals(site)) return "https://www.ebay.co.uk";
        if ("德国".equals(site)) return "https://www.ebay.de";
        return null;
    }

    // ====================================================================
    // 品牌归属
    // ====================================================================

    /**
     * 根据 SKU 前缀匹配品牌负责人。
     * @param sku           SKU 编码
     * @param ownerByBrand  brandCode(大写) → ownerName 映射
     * @return 负责人姓名，未匹配返回空字符串
     */
    public static String matchOwner(String sku, Map<String, String> ownerByBrand) {
        if (!StringUtils.hasText(sku)) return "";
        int i = sku.indexOf('-');
        return ownerByBrand.getOrDefault(i > 0 ? sku.substring(0, i).toUpperCase() : sku.toUpperCase(), "");
    }

    // ====================================================================
    // 数学工具
    // ====================================================================

    /** 安全除法，除数为 0 返回 BigDecimal.ZERO */
    public static BigDecimal safeDivide(int numerator, int denominator) {
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    // ====================================================================
    // SKU 产品等级
    // ====================================================================

    /**
     * 计算 SKU 产品等级（S/A/B/C/D/E），与采购计划备注逻辑一致。
     * @param sales30d   近30天销量
     * @param profitRate 近30天毛利率（百分比），如 7.95 表示 7.95%
     */
    public static String calcProductLevel(int sales30d, double profitRate) {
        if (sales30d >= 30 && profitRate >= 20) return "S";
        if (sales30d >= 15 && profitRate >= 20) return "A";
        if (sales30d >= 10 && profitRate >= 18) return "B";
        if ((sales30d >= 10 && profitRate >= 10) || (sales30d >= 5 && sales30d < 10 && profitRate >= 15)) return "C";
        if ((sales30d < 5 && profitRate >= 15) || (sales30d >= 5 && sales30d < 10 && profitRate >= 10 && profitRate < 15))
            return "D";
        return "E";
    }
}
