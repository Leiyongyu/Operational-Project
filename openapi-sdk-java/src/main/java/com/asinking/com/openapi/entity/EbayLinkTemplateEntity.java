package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * eBay 链接模板表 ebay_link_template。
 * 按站点存储售前/售后链接模板，{oe} 占位符运行时替换。
 */
@Data
@TableName("ebay_link_template")
public class EbayLinkTemplateEntity {

    @TableId("site")
    private String site;

    @TableField("presale_url")
    private String presaleUrl;

    @TableField("sold_url")
    private String soldUrl;
}
