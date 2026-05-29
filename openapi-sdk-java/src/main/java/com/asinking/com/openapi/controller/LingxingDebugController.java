package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.service.LingxingAuthService;
import com.asinking.com.openapi.service.LingxingEbayService;
import com.asinking.com.openapi.service.LingxingPlatformOrderService;
import com.asinking.com.openapi.service.LingxingShopService;
import com.asinking.com.openapi.service.model.EbayListRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 领星调试接口，通过 X-Client-Id + X-Api-Key 认证，用于直接查看领星 API 返回数据。
 */
@RestController
@RequestMapping("/api/lingxing")
public class LingxingDebugController {

    private final LingxingAuthService authService;
    private final LingxingShopService shopService;
    private final LingxingEbayService ebayService;
    private final LingxingPlatformOrderService platformOrderService;

    /** 构造器注入领星调试所需的各类服务。 */
    public LingxingDebugController(LingxingAuthService authService, LingxingShopService shopService,
                                   LingxingEbayService ebayService, LingxingPlatformOrderService platformOrderService) {
        this.authService = authService;
        this.shopService = shopService;
        this.ebayService = ebayService;
        this.platformOrderService = platformOrderService;
    }

    /**
     * 获取领星 access_token（调试用）。
     */
    @GetMapping("/token")
    public Object getToken() throws Exception {
        return authService.getAccessTokenResponse();
    }

    /**
     * 查询启用的 eBay 店铺列表。
     */
    @GetMapping("/shops/ebay")
    public Object getActiveEbayShops(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "200") int length) throws Exception {
        return shopService.getActiveEbayShops(offset, length);
    }

    /**
     * 分页查询 eBay 商品 listing。
     */
    @PostMapping("/ebay/list")
    public Object listEbayItems(@RequestBody(required = false) EbayListRequest req) throws Exception {
        return ebayService.listEbayItems(req);
    }

    /**
     * 全量同步 eBay 商品 listing 到本地库。
     */
    @PostMapping("/ebay/list/all")
    public Object syncAllEbayItems(@RequestBody(required = false) EbayListRequest req) throws Exception {
        return ebayService.syncAllEbayItems(req);
    }

    /** 测试：获取平台订单并查发货详情 */
    @GetMapping("/order/test")
    public Object testOrder(@RequestParam(defaultValue = "2026-05-01 00:00:00") String startDate,
                            @RequestParam(defaultValue = "2026-05-25 23:59:59") String endDate) throws Exception {
        return platformOrderService.testFetchOne(startDate, endDate);
    }
}
