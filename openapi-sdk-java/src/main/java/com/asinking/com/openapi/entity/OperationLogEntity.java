package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLogEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("api_path")
    private String apiPath;          // 接口路径

    @TableField("http_method")
    private String httpMethod;       // HTTP方法

    @TableField("operator")
    private String operator;         // 操作人账号

    @TableField("ip_address")
    private String ipAddress;        // IP地址

    @TableField("operation_type")
    private String operationType;    // 导入/同步/拉取

    @TableField("target")
    private String target;           // 目标表名或接口名

    @TableField("status")
    private String status;           // 成功/失败

    @TableField("total_count")
    private Integer totalCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
