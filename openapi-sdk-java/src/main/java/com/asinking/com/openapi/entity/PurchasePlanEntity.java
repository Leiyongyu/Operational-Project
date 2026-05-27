package com.asinking.com.openapi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("purchase_plan")
public class PurchasePlanEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("plan_sn") private String planSn;
    @TableField("ppg_sn") private String ppgSn;
    @TableField("product_name") private String productName;
    @TableField("sku") private String sku;
    @TableField("fnsku") private String fnsku;
    @TableField("pic_url") private String picUrl;
    @TableField("supplier_id") private String supplierId;
    @TableField("supplier_name") private String supplierName;
    @TableField("status_text") private String statusText;
    @TableField("status") private Integer status;
    @TableField("sid") private String sid;
    @TableField("seller_name") private String sellerName;
    @TableField("marketplace") private String marketplace;
    @TableField("expect_arrive_time") private String expectArriveTime;
    @TableField("remark") private String remark;
    @TableField("quantity_plan") private Integer quantityPlan;
    @TableField("product_id") private Integer productId;
    @TableField("cg_uid") private Integer cgUid;
    @TableField("cg_opt_username") private String cgOptUsername;
    @TableField("cg_box_pcs") private Integer cgBoxPcs;
    @TableField("is_combo") private Integer isCombo;
    @TableField("is_aux") private Integer isAux;
    @TableField("is_related_process_plan") private Integer isRelatedProcessPlan;
    @TableField("spu") private String spu;
    @TableField("spu_name") private String spuName;
    @TableField("creator_uid") private Integer creatorUid;
    @TableField("creator_real_name") private String creatorRealName;
    @TableField("wid") private Integer wid;
    @TableField("warehouse_name") private String warehouseName;
    @TableField("purchaser_id") private Integer purchaserId;
    @TableField("purchaser_name") private String purchaserName;
    @TableField("create_time") private String createTime;
    @TableField("plan_remark") private String planRemark;
    @TableField("attribute_json") private String attributeJson;
    @TableField("file_json") private String fileJson;
    @TableField("msku_json") private String mskuJson;
    @TableField("perm_uid_json") private String permUidJson;
    @TableField("perm_username_json") private String permUsernameJson;

    @TableField(value = "upload_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime uploadTime;
}
