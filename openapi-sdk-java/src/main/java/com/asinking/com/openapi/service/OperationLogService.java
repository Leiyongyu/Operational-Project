package com.asinking.com.openapi.service;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.entity.OperationLogEntity;
import com.asinking.com.openapi.mapper.mp.OperationLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class OperationLogService {

    private final OperationLogMapper mapper;

    public OperationLogService(OperationLogMapper mapper) { this.mapper = mapper; }

    /** AOP 切面使用：写入含HTTP信息的完整日志 */
    public void log(String apiPath, String httpMethod, String operator, String ipAddress,
                    String operationType, String target, String status,
                    Integer total, Integer success, Integer fail, String error) {
        OperationLogEntity e = new OperationLogEntity();
        e.setApiPath(apiPath);
        e.setHttpMethod(httpMethod);
        e.setOperator(operator);
        e.setIpAddress(ipAddress);
        e.setOperationType(operationType);
        e.setTarget(target);
        e.setStatus(status);
        e.setTotalCount(total);
        e.setSuccessCount(success);
        e.setFailCount(fail);
        if (error != null && error.length() > 500) error = error.substring(0, 500);
        e.setErrorMessage(error);
        e.setCreateTime(LocalDateTime.now());
        mapper.insert(e);
    }

    /** 定时任务使用：无HTTP信息 */
    public void log(String operationType, String target, String status,
                    Integer total, Integer success, Integer fail, String error) {
        log(null, null, "SYSTEM", null, operationType, target, status, total, success, fail, error);
    }

    /** 删除15天前的日志 */
    public int cleanOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(15);
        return mapper.delete(new LambdaQueryWrapper<OperationLogEntity>()
                .lt(OperationLogEntity::getCreateTime, cutoff));
    }

    /** 分页查询 */
    public PageResult<OperationLogEntity> page(long page, long size) {
        long p = page <= 0 ? 1 : page;
        long s = size <= 0 ? 20 : Math.min(size, 100);
        long total = mapper.selectCount(null);
        var list = mapper.selectList(
                new LambdaQueryWrapper<OperationLogEntity>()
                        .orderByDesc(OperationLogEntity::getId)
                        .last("LIMIT " + ((p - 1) * s) + "," + s));
        return new PageResult<>(total, p, s, list);
    }
}
