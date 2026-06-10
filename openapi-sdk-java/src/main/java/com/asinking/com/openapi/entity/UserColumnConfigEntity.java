package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_column_config")
public class UserColumnConfigEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("user_account")
    private String userAccount;

    @TableField("page_key")
    private String pageKey;

    @TableField("config_json")
    private String configJson;

    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
