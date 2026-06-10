package com.asinking.com.openapi.common.annotation;

import java.lang.annotation.*;

/**
 * AOP 操作日志注解：标注在 Controller 方法上自动记录操作日志。
 * 切面会解析返回值中的 inserted/updated/skipped/total 等字段。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 操作类型：导入 / 同步 / 拉取 */
    String value();

    /** 操作目标描述（默认空，AOP 会用接口路径填充） */
    String target() default "";
}
