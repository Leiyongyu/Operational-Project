package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.service.GoodcangClient;
import com.asinking.com.openapi.service.GoodcangSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 谷仓(GoodCang) API 推送订阅回调 + 同步 + 调试接口。
 */
@RestController
@RequestMapping("/api/goodcang")
public class GoodcangCallbackController {

    private static final Logger LOG = LoggerFactory.getLogger(GoodcangCallbackController.class);
    private final GoodcangClient client;
    private final GoodcangSyncService syncService;

    public GoodcangCallbackController(GoodcangClient client, GoodcangSyncService syncService) {
        this.client = client;
        this.syncService = syncService;
    }

    /** 接收谷仓推送的回调通知，确认返回 SUCCESS。 */
    @PostMapping("/callback")
    public Map<String, String> callback(@RequestBody(required = false) String rawBody) {
        LOG.info("谷仓推送收到: {}", rawBody != null ? rawBody.substring(0, Math.min(500, rawBody.length())) : "(空)");
        Map<String, String> resp = new LinkedHashMap<>();
        resp.put("Status", "SUCCESS");
        return resp;
    }

    /** 调试：获取入库单列表 */
    @GetMapping("/test/grn-list")
    public Object testGrnList(@RequestParam(defaultValue = "2026-05-01 00:00:00") String from,
                              @RequestParam(defaultValue = "2026-05-25 23:59:59") String to) throws Exception {
        Map<String, Object> resp = client.getGrnList(from, to, 1, 5);

        // 提取关键字段
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ask", resp.get("ask"));
        result.put("count", resp.get("count"));
        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
        if (data != null && !data.isEmpty()) {
            List<Map<String, Object>> samples = new ArrayList<>();
            for (Map<String, Object> item : data.subList(0, Math.min(5, data.size()))) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("receiving_code", item.get("receiving_code"));
                s.put("warehouse_code", item.get("warehouse_code"));
                s.put("create_at", item.get("create_at"));
                s.put("receiving_status", item.get("receiving_status"));
                samples.add(s);
            }
            result.put("samples", samples);
        }
        return result;
    }

    /** 调试：获取入库单明细 */
    @GetMapping("/test/grn-detail")
    public Object testGrnDetail(@RequestParam String code) throws Exception {
        return client.getGrnDetail(code);
    }

    /** 同步仓库信息 */
    @PostMapping("/sync-warehouse")
    public Object syncWarehouse() throws Exception {
        return syncService.syncWarehouses();
    }

    /** 同步入库单数据。 */
    @PostMapping("/sync-grn")
    public Object syncGrn(
            @RequestParam(defaultValue = "2026-01-01 00:00:00") String from,
            @RequestParam(required = false) String to) throws Exception {
        if (to == null) to = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return syncService.syncGrn(from, to);
    }
}
