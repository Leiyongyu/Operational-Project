package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("team")
/** 团队表 team（组长-组员关系） */
public class TeamEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("leader")
    private String leader;

    @TableField("member")
    private String member;

    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;
}
