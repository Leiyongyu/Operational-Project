package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.entity.OperationLogEntity;
import com.asinking.com.openapi.service.OperationLogService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogService service;

    public OperationLogController(OperationLogService service) { this.service = service; }

    @GetMapping
    public Result<PageResult<OperationLogEntity>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(service.page(page, size));
    }

    /** 清除15天前的日志 */
    @DeleteMapping("/cleanup")
    public Result<Map<String, Object>> cleanup() {
        int deleted = service.cleanOldLogs();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", deleted);
        return Result.ok(data);
    }
}
