package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inventory_snapshot")
public class InventorySnapshotEntity {

    @TableId("id")
    private Integer id;

    @TableField("data_json")
    private String dataJson;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
