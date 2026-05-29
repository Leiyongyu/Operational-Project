# JMH 跨境电商中台系统 API 文档

> 项目：`openapi-sdk-java` | Spring Boot 2.7.18 | Java 17 | MyBatis Plus 3.5.5

---

## 目录

1. [概述](#概述)
2. [认证机制](#认证机制)
3. [通用响应格式](#通用响应格式)
4. [通用错误码](#通用错误码)
5. [用户认证](#用户认证)
6. [用户管理](#用户管理)
7. [品牌负责人管理](#品牌负责人管理)
8. [仓库管理](#仓库管理)
9. [仓库库存明细](#仓库库存明细)
10. [库存总览](#库存总览)
11. [领星调试接口](#领星调试接口)
12. [数据同步](#数据同步)
13. [谷仓回调与同步](#谷仓回调与同步)
14. [利润报表上传](#利润报表上传)
16. [采购计划](#采购计划)
17. [eBay 销量上传](#ebay-销量上传)
18. [定时任务](#定时任务)
19. [全局异常处理](#全局异常处理)
20. [数据实体参考](#数据实体参考)

---

## 概述

本系统为跨境电商中台系统，集成**领星（Lingxing）ERP**、**谷仓（Goodcang）WMS** 等第三方平台，提供库存管理、采购计划、销量统计、利润分析等功能。

**基础URL：** `http://{host}:{port}`

---

## 认证机制

系统使用两种认证方式：

### JWT 认证（适用接口：`/api/**`）

| 配置项 | 说明 |
|--------|------|
| Header | `Authorization: Bearer <token>` |
| Token 过期 | 由 `jwt.ttlSeconds` 配置决定 |
| 黑名单 | 登出后 JTI 加入内存黑名单，Token 立刻失效 |

**排除路径（无需 JWT）：**
- `/api/user/login`
- `/api/lingxing/**`

- `/api/goodcang/**`

### API Key 认证（适用接口：`/api/lingxing/**`）

| Header | 说明 |
|--------|------|
| `X-Client-Id` | 客户端 ID |
| `X-Api-Key` | API 密钥 |

---

## 通用响应格式

### 标准响应 `Result<T>`

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 状态码，0=成功 |
| message | string | 状态信息 |
| data | T | 响应数据 |

### 分页响应 `PageResult<T>`

```json
{
  "total": 100,
  "page": 1,
  "size": 20,
  "records": []
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| total | long | 总记录数 |
| page | long | 当前页码 |
| size | long | 每页条数 |
| records | List\<T\> | 当前页数据 |

---

## 通用错误码

| 错误码 | 枚举值 | 说明 |
|--------|--------|------|
| 0 | SUCCESS | 成功 |
| 400 | BAD_REQUEST | 请求参数错误 |
| 401 | UNAUTHORIZED | 未认证 |
| 409 | CONFLICT | 数据冲突（如重复键） |
| 500 | SERVER_ERROR | 服务器内部错误 |

---

## 用户认证

### POST /api/user/login — 用户登录

**认证：** 无需

**请求体：**

```json
{
  "account": "admin",
  "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| account | string | 是 | 用户账号 |
| password | string | 是 | 用户密码 |

**响应 (`Result<UserLoginResponse>`)：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresAtMillis": 1700000000000,
    "account": "admin",
    "role": "admin",
    "ownerName": "张三"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| token | string | JWT 令牌 |
| tokenType | string | 固定为 "Bearer" |
| expiresAtMillis | long | 过期时间戳（毫秒） |
| account | string | 用户账号 |
| role | string | 用户角色 |
| ownerName | string | 关联品牌负责人 |

### POST /api/user/logout — 用户登出

**认证：** 无需（但需要传 Authorization header）

**请求头：**
| Header | 说明 |
|--------|------|
| Authorization | `Bearer <token>` |

**响应 (`Result<Map>`)：**
```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

---

## 用户管理

### POST /api/users — 创建用户

**认证：** JWT

**请求体：**

```json
{
  "account": "newuser",
  "password": "pass123",
  "role": "user",
  "ownerName": "张三",
  "brandCode": "BR001"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| account | string | 是 | 用户账号 |
| password | string | 是 | 用户密码 |
| role | string | 否 | 角色（admin/user） |
| ownerName | string | 否 | 品牌负责人名称 |
| brandCode | string | 否 | 品牌编码 |

**响应 (`Result<UserResponse>`)：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "1",
    "account": "newuser",
    "role": "user",
    "ownerName": "张三",
    "owners": ["张三"],
    "createTime": "2025-01-01T12:00:00",
    "updateTime": "2025-01-01T12:00:00"
  }
}
```

### PUT /api/users/{id} — 更新用户

**认证：** JWT

**路径参数：**
| 参数 | 类型 | 说明 |
|------|------|------|
| id | string | 用户 ID |

**请求体：**

```json
{
  "role": "admin",
  "ownerName": "李四",
  "password": "newpass456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| role | string | 否 | 新角色 |
| ownerName | string | 否 | 新品牌负责人 |
| password | string | 否 | 新密码 |

**响应 (`Result<UserResponse>`)：** 同创建用户响应

### DELETE /api/users/{id} — 删除用户

**认证：** JWT

**路径参数：**
| 参数 | 类型 | 说明 |
|------|------|------|
| id | string | 用户 ID |

**响应 (`Result<Map>`)：**
```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

### GET /api/users/{id} — 获取用户详情

**认证：** JWT

**路径参数：**
| 参数 | 类型 | 说明 |
|------|------|------|
| id | string | 用户 ID |

**响应 (`Result<UserResponse>`)：** 同创建用户响应

### GET /api/users — 分页查询用户列表

**认证：** JWT

**查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | long | 否 | 1 | 页码 |
| size | long | 否 | 10 | 每页条数 |
| account | string | 否 | — | 账号模糊筛选 |
| role | string | 否 | — | 角色精确筛选 |

**响应 (`Result<PageResult<UserResponse>>`)：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 50,
    "page": 1,
    "size": 10,
    "records": [
      {
        "id": "1",
        "account": "admin",
        "role": "admin",
        "ownerName": "张三",
        "owners": ["张三"],
        "createTime": "2025-01-01T12:00:00",
        "updateTime": "2025-01-01T12:00:00"
      }
    ]
  }
}
```

---

## 品牌负责人管理

### POST /api/brand-owners — 创建品牌负责人

**认证：** JWT

**请求体：**

```json
{
  "brandCode": "BR001",
  "ownerName": "张三"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| brandCode | string | 是 | 品牌编码 |
| ownerName | string | 是 | 负责人姓名 |

**响应 (`Result<BrandOwnerResponse>`)：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "brandCode": "BR001",
    "ownerName": "张三"
  }
}
```

### PUT /api/brand-owners/{id} — 更新品牌负责人

**认证：** JWT

**路径参数：**
| 参数 | 类型 | 说明 |
|------|------|------|
| id | int | 品牌负责人记录 ID |

**请求体：**

```json
{
  "brandCode": "BR002",
  "ownerName": "李四"
}
```

**响应 (`Result<BrandOwnerResponse>`)：** 同创建响应

### GET /api/brand-owners/{id} — 获取品牌负责人详情

**认证：** JWT

**路径参数：**
| 参数 | 类型 | 说明 |
|------|------|------|
| id | int | 品牌负责人记录 ID |

**响应 (`Result<BrandOwnerResponse>`)：** 同创建响应

### GET /api/brand-owners — 分页查询品牌负责人

**认证：** JWT

**查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | long | 否 | 1 | 页码 |
| size | long | 否 | 10 | 每页条数 |
| brandCode | string | 否 | — | 品牌编码筛选 |
| ownerName | string | 否 | — | 负责人筛选 |

**响应 (`Result<PageResult<BrandOwnerResponse>>`)：** 分页列表

### DELETE /api/brand-owners/{id} — 删除品牌负责人

**认证：** JWT

**路径参数：**
| 参数 | 类型 | 说明 |
|------|------|------|
| id | int | 品牌负责人记录 ID |

**响应 (`Result<Map>`)：**
```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

---

## 仓库管理

### GET /api/warehouse/warehouses — 分页查询仓库列表

**认证：** JWT

**查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | long | 否 | 1 | 页码 |
| size | long | 否 | 10 | 每页条数 |
| wid | string | 否 | — | 仓库 ID 筛选 |
| warehouseName | string | 否 | — | 仓库名称模糊筛选 |

**响应 (`Result<PageResult<WarehouseEntity>>`)**

### POST /api/warehouse/warehouses/sync — 同步仓库

**认证：** JWT

从领星 API 同步仓库数据（类型 1+3），增量 upsert 到本地数据库。

**请求体（可选）：**

```json
{
  "offset": 0,
  "length": 100
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| offset | int | 否 | 分页偏移量 |
| length | int | 否 | 每页条数 |

**响应 (`Result<Object>`)**

---

## 仓库库存明细

### GET /api/warehouse/inventory-details — 分页查询库存明细

**认证：** JWT

**查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | long | 否 | 1 | 页码 |
| size | long | 否 | 10 | 每页条数 |
| wid | string | 否 | — | 仓库 ID 筛选 |
| productId | string | 否 | — | 商品 ID 筛选 |
| sku | string | 否 | — | SKU 筛选 |

**响应 (`Result<PageResult<WarehouseInventoryDetailEntity>>`)**

### POST /api/warehouse/inventory-details/sync — 增量同步库存明细

**认证：** JWT

同步指定仓库的库存明细（增量 upsert）。

**请求体：**

```json
{
  "wid": "123",
  "offset": 0,
  "length": 200,
  "sku": ""
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| wid | string | 是 | 仓库 ID |
| offset | int | 否 | 分页偏移量 |
| length | int | 否 | 每页条数 |
| sku | string | 否 | SKU 筛选 |

**响应 (`Result<Object>`)**

### POST /api/warehouse/inventory-details/sync-all — 全量同步库存明细

**认证：** JWT

删除所有库存明细后重新从领星拉取全部数据。

**请求体（可选）：**

```json
{
  "length": 200,
  "sku": ""
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| length | int | 否 | 200 | 每页条数 |
| sku | string | 否 | — | SKU 筛选 |

**响应 (`Result<Object>`)**

---

## 库存总览

### GET /api/inventory-overview — 库存总览列表

**认证：** JWT

返回过滤后的库存总览数据。admin 角色返回全部，普通用户按品牌负责人过滤。

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sku | string | 否 | SKU 筛选 |
| warehouse | string | 否 | 仓库筛选 |

**响应 (`Result<List<InventoryOverviewItem>>`)：**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "warehouseNames": "美国仓A, 美国仓B",
      "sku": "ABC-123",
      "productName": "某商品名称",
      "last30DaysProfit": 25.50,
      "overseasOnway": 100,
      "overseasSellable": 500,
      "overseasTotal": 600,
      "purchasePendingDelivery": 200,
      "localOnway": 50,
      "localSellable": 300,
      "purchasePlan": "",
      "lockNum": 10,
      "totalInventory": 950,
      "last7DaysSales": 30,
      "last30DaysSales": 120,
      "last90DaysSales": 350,
      "maxMonthlySales": null,
      "overseasInStockRatio": 4.17,
      "overseasTotalRatio": 5.00,
      "totalInventoryRatio": 7.92,
      "lastLocalOutboundTime": null,
      "outboundDays": null,
      "purchaseCycle": null,
      "purchaseQuantity": 150.00,
      "maxMonthlyReplenish": null,
      "owner": "张三"
    }
  ]
}
```

**InventoryOverviewItem 字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| warehouseNames | string | 仓库名称（多个逗号分隔） |
| sku | string | SKU 编码 |
| productName | string | 商品名称 |
| last30DaysProfit | BigDecimal | 近30天毛利率（%） |
| overseasOnway | int | 海外在途（调拨中） |
| overseasSellable | int | 海外可售 |
| overseasTotal | int | 海外合计（可售+在途） |
| purchasePendingDelivery | int | 采购待交付 |
| localOnway | int | 本地在途（成都） |
| localSellable | int | 本地可售 |
| purchasePlan | string | 采购计划（预留） |
| lockNum | int | 待出库（已锁定） |
| totalInventory | int | 总库存 |
| last7DaysSales | int | 近7天销量 |
| last30DaysSales | int | 近30天销量 |
| last90DaysSales | int | 近90天销量 |
| maxMonthlySales | int | 历史最高月销量（预留） |
| overseasInStockRatio | BigDecimal | 海外可售/近30天销量 |
| overseasTotalRatio | BigDecimal | 海外合计/近30天销量 |
| totalInventoryRatio | BigDecimal | 总库存/近30天销量 |
| lastLocalOutboundTime | string | 最近本地出库时间（预留） |
| outboundDays | int | 出库天数（预留） |
| purchaseCycle | int | 采购周期（预留） |
| purchaseQuantity | BigDecimal | 建议采购量 |
| maxMonthlyReplenish | int | 最大月度补货量（预留） |
| owner | string | 负责人（按SKU前缀匹配品牌） |

### GET /api/inventory-overview/warehouses — 仓库下拉选项

**认证：** JWT

**响应 (`Result<List<WarehouseOptionItem>>`)：**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "label": "美国",
      "wids": "123,456,789"
    },
    {
      "label": "英国",
      "wids": "321,654"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| label | string | 国家名称 |
| wids | string | 该国家下仓库 ID 列表（逗号分隔） |

---

## 领星调试接口

> 所有接口使用 **API Key 认证** (`X-Client-Id` + `X-Api-Key`)

### GET /api/lingxing/token — 获取领星 Access Token

**认证：** API Key

**响应：** 领星 access_token API 原始响应

### GET /api/lingxing/shops/ebay — 获取 eBay 店铺列表

**认证：** API Key

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| offset | int | 否 | 分页偏移量 |
| length | int | 否 | 每页条数 |

**响应：** 领星激活的 eBay 店铺列表（platform_code=10003, status=1, is_sync=1）

### POST /api/lingxing/ebay/list — 查询 eBay 商品列表（分页）

**认证：** API Key

**请求体（可选）：**

```json
{
  "offset": 0,
  "length": 100,
  "storeIds": ["store1"],
  "siteCode": ["US"],
  "listingStatus": [1],
  "autoRestocks": [1],
  "listingType": [1],
  "searchField": 1,
  "searchSingleValue": "keyword",
  "listingTimeField": 1,
  "listingStartTime": "2025-01-01",
  "listingEndTime": "2025-12-31"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| offset | int | 否 | 分页偏移量 |
| length | int | 否 | 每页条数 |
| storeIds | List\<string\> | 否 | 店铺 ID 列表 |
| siteCode | List\<string\> | 否 | 站点代码（如 US, UK） |
| listingStatus | List\<int\> | 否 | 刊登状态 |
| autoRestocks | List\<int\> | 否 | 自动补货设置 |
| listingType | List\<int\> | 否 | 刊登类型 |
| searchField | int | 否 | 搜索字段 |
| searchSingleValue | string | 否 | 搜索关键词 |
| listingTimeField | int | 否 | 时间字段 |
| listingStartTime | string | 否 | 刊登开始时间 |
| listingEndTime | string | 否 | 刊登结束时间 |

**响应：** 增量同步结果（upsert 到本地 DB）

### POST /api/lingxing/ebay/list/all — 全量同步 eBay 商品

**认证：** API Key

**请求参数：** 同 `/api/lingxing/ebay/list`（分页参数用于控制每页大小）

**说明：** 分页循环拉取（最多 10000 页），全量 upsert 到本地。

### GET /api/lingxing/order/test — 测试获取平台订单

**认证：** API Key

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| startDate | string | 是 | 开始日期 |
| endDate | string | 是 | 结束日期 |

**响应：** 平台订单及物流详情原始响应

---

## 数据同步

> 所有接口使用 **JWT 认证**

### POST /api/sync/all — 一键全量同步

**认证：** JWT

依次同步：仓库 → 库存明细，返回各步骤耗时。

**响应 (`Result<Map>`)：**
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "仓库同步(ms)": 1234,
    "库存明细同步(ms)": 5678
  }
}
```

### POST /api/sync/statement — 同步库存流水

**认证：** JWT

同步近 90 天仓库库存流水（按 wid+sku+opt_time 去重 upsert）。

**响应 (`Result<Object>`)**

### POST /api/sync/purchase-order — 同步采购订单

**认证：** JWT

同步前一天的采购订单（按 order_sn+create_time 去重 upsert）。

**响应 (`Result<Object>`)**

### POST /api/sync/purchase-order/init — 初始同步采购订单

**认证：** JWT

同步近 90 天全量采购订单数据。

**响应 (`Result<Object>`)**

### POST /api/sync/purchase-plan — 同步采购计划

**认证：** JWT

同步前一天的采购计划（按 plan_sn+sku 去重 upsert）。

**响应 (`Result<Object>`)**

### POST /api/sync/purchase-plan/init — 初始同步采购计划

**认证：** JWT

同步近 90 天全量采购计划数据。

**响应 (`Result<Object>`)**

---

## 谷仓回调与同步

> 无需 JWT 认证

### POST /api/goodcang/callback — 谷仓 Webhook 回调

**认证：** 无需

接收谷仓 WMS 系统的回调通知。

**请求体：** raw body（可选）

**响应 (`Map<String, String>`)：**
```json
{
  "code": "0",
  "message": "success"
}
```

### GET /api/goodcang/test/grn-list — 测试：获取 GRN 列表

**认证：** 无需

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| from | string | 是 | 开始日期 |
| to | string | 是 | 结束日期 |

**响应：** 前 5 条 GRN 列表

### GET /api/goodcang/test/grn-detail — 测试：获取 GRN 详情

**认证：** 无需

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | string | 是 | GRN 收货单号 (receiving_code) |

**响应：** GRN 详情

### POST /api/goodcang/sync-warehouse — 同步谷仓仓库

**认证：** 无需

删除本地全部谷仓仓库数据后重新拉取并入库，同时模糊匹配领星 wid。

**响应 (`Result<Object>`)**

### POST /api/goodcang/sync-grn — 同步谷仓 GRN

**认证：** 无需

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| from | string | 是 | 开始日期 |
| to | string | 否 | 结束日期 |

**响应 (`Result<Object>`)**

---

---

## 利润报表上传

### POST /api/profit-report/upload — 上传利润报表

**认证：** JWT

**Content-Type：** `multipart/form-data`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | 是 | Excel 文件（.xlsx/.xls） |

**处理逻辑：** 按 (msku, ship_time, store_name, country_code) 联合键 upsert。

**响应 (`Result<Map>`)：**
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "processed": 150,
    "message": "导入完成"
  }
}
```

**Excel 列映射：**
| 列名 | 数据库字段 | 说明 |
|------|-----------|------|
| MSKU | msku | 商品 MSKU |
| 发货时间 | ship_time | 发货日期 |
| 店铺名称 | store_name | 店铺 |
| 国家代码 | country_code | 国家 |
| 币种 | currency_code | 货币 |
| 平台 | platform | 平台名称 |
| 销量 | volume | 销量 |
| 销售额 | sales_amount | 销售额 |
| 毛利 | gross_profit | 毛利 |
| 毛利率 | gross_margin | 毛利率 |
| 采购成本 | purchase_cost | 采购成本 |
| 物流成本 | logistics_cost | 物流成本 |

---

## 采购计划

> 所有接口使用 **JWT 认证**

### POST /api/purchase-plan/upload — 上传采购计划 Excel

**认证：** JWT

**Content-Type：** `multipart/form-data`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | 是 | Excel 文件 |

解析 Excel 后调用领星 API 创建采购计划。

**响应 (`Result<PurchasePlanCreateResponse>`)：**
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "ppgSn": "PPG20250101001",
    "planSn": ["PLAN001", "PLAN002"]
  }
}
```

| 字段 | JSON Key | 类型 | 说明 |
|------|----------|------|------|
| ppgSn | ppg_sn | string | 采购计划组编号 |
| planSn | plan_sn | List\<string\> | 计划编号列表 |

### POST /api/purchase-plan/create — 创建采购计划（JSON）

**认证：** JWT

**请求体：**

```json
[
  {
    "wid": 123,
    "sku": "ABC-001",
    "quantity": 100,
    "delivery_date": "2025-02-01"
  }
]
```

**响应 (`Result<PurchasePlanCreateResponse>`)：** 同上传接口

### GET /api/purchase-plan/skus — SKU 搜索自动补全

**认证：** JWT

**查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| keyword | string | 否 | "" | 搜索关键词 |

从 eBay 商品列表中搜索，返回前 500 条匹配结果。

**响应 (`Result<List<Map>>`)**

### GET /api/purchase-plan/stores — 店铺搜索自动补全

**认证：** JWT

**查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| keyword | string | 否 | "" | 搜索关键词 |

从 eBay 店铺列表中搜索，返回前 50 条。

**响应 (`Result<List<Map>>`)**

### GET /api/purchase-plan/warehouses — 仓库搜索自动补全

**认证：** JWT

**查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| keyword | string | 否 | "" | 搜索关键词 |

返回前 50 条仓库匹配结果。

**响应 (`Result<List<Map>>`)**

---

## eBay 销量上传

### POST /api/ebay-sales/upload — 上传 eBay 销量 Excel

**认证：** JWT

**Content-Type：** `multipart/form-data`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | 是 | Excel 文件 |

按 (platform_order_no, sku) 联合键 upsert。

**响应 (`Result<Map>`)：**
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "processed": 200,
    "message": "导入完成"
  }
}
```

**Excel 列映射：**
| 列名 | 数据库字段 | 说明 |
|------|-----------|------|
| 平台订单号 | platform_order_no | 平台订单编号 |
| SKU | sku | 商品 SKU |
| 币种 | currency | 货币 |
| 数量 | quantity | 销售数量 |
| 付款时间 | payment_time | 付款时间 |

---

## 定时任务

系统内置 8 个定时任务，均在凌晨执行：

| Cron | 时间 | 任务 | 说明 |
|------|------|------|------|
| `0 0 0 * * ?` | 00:00 | syncWarehouses | 每日仓库同步 |
| `0 3 0 * * ?` | 00:03 | syncEbayItems | 每日 eBay 商品同步 |
| `0 5 0 * * ?` | 00:05 | syncInventory | 每日库存明细同步 |
| `0 20 0 * * ?` | 00:20 | syncGoodcangWarehouses | 每日谷仓仓库同步 |
| `0 25 0 * * ?` | 00:25 | syncGoodcangGrn | 每日谷仓 GRN 同步 |
| `0 30 0 * * ?` | 00:30 | syncWarehouseStatement | 每日库存流水同步（近90天） |
| `0 35 0 * * ?` | 00:35 | syncPurchaseOrder | 每日采购订单同步（前一天） |
| `0 40 0 * * ?` | 00:40 | syncPurchasePlan | 每日采购计划同步（前一天） |

---

## 全局异常处理

| 异常类型 | HTTP 状态码 | 响应 code | 说明 |
|----------|-------------|-----------|------|
| `BusinessException` | 200 | 异常携带的 ResultCode | 业务异常 |
| `IllegalArgumentException` | 200 | 400 | 参数校验失败 |
| `DuplicateKeyException` | 200 | 409 | 数据库唯一键冲突（"数据已存在"） |
| 其他 `Exception` | 200 | 500 | 服务器内部错误 |

---

## 数据实体参考

### 核心实体与表映射

| 实体类 | 数据库表 | 说明 |
|--------|----------|------|
| UserEntity | user | 系统用户 |
| BrandOwnerEntity | brand_owner | 品牌负责人关联 |
| WarehouseEntity | warehouse | 仓库（领星同步） |
| WarehouseInventoryDetailEntity | warehouse_inventory_detail | 仓库库存明细 |
| EbayShopListEntity | ebay_shop_list | eBay 店铺列表 |
| EbayProductListingEntity | ebay_product_listing | eBay 商品刊登 |
| ProfitReportEntity | profit_report | 利润报表 |
| WarehouseStatementEntity | warehouse_statement | 库存流水 |
| PurchaseOrderEntity | purchase_order | 采购订单 |
| PurchasePlanEntity | purchase_plan | 采购计划 |
| EbaySalesEntity | ebay_sales | eBay 销量 |
| GoodcangGrnListEntity | goodcang_grn_list | 谷仓 GRN 列表 |
| GoodcangGrnDetailEntity | goodcang_grn_detail | 谷仓 GRN 明细 |
| GoodcangWarehouseEntity | goodcang_warehouse | 谷仓仓库 |

### 请求头中的 JWT 属性

登录后，JWT 拦截器会解析并向 Controller 传入以下 Request Attribute：

| Attribute Key | 说明 |
|---------------|------|
| `jwt.uid` | 用户 ID |
| `jwt.account` | 用户账号 |
| `jwt.role` | 用户角色 |
| `jwt.jti` | JWT ID（用于黑名单） |

---

## 接口汇总

| 模块 | HTTP 方法 | 路径 | 认证 | 说明 |
|------|-----------|------|------|------|
| 用户认证 | POST | /api/user/login | 无 | 用户登录 |
| 用户认证 | POST | /api/user/logout | 无 | 用户登出 |
| 用户管理 | POST | /api/users | JWT | 创建用户 |
| 用户管理 | PUT | /api/users/{id} | JWT | 更新用户 |
| 用户管理 | DELETE | /api/users/{id} | JWT | 删除用户 |
| 用户管理 | GET | /api/users/{id} | JWT | 用户详情 |
| 用户管理 | GET | /api/users | JWT | 用户列表 |
| 品牌负责人 | POST | /api/brand-owners | JWT | 创建品牌负责人 |
| 品牌负责人 | PUT | /api/brand-owners/{id} | JWT | 更新品牌负责人 |
| 品牌负责人 | GET | /api/brand-owners/{id} | JWT | 品牌负责人详情 |
| 品牌负责人 | GET | /api/brand-owners | JWT | 品牌负责人列表 |
| 品牌负责人 | DELETE | /api/brand-owners/{id} | JWT | 删除品牌负责人 |
| 仓库 | GET | /api/warehouse/warehouses | JWT | 仓库列表 |
| 仓库 | POST | /api/warehouse/warehouses/sync | JWT | 同步仓库 |
| 库存明细 | GET | /api/warehouse/inventory-details | JWT | 库存明细列表 |
| 库存明细 | POST | /api/warehouse/inventory-details/sync | JWT | 增量同步库存 |
| 库存明细 | POST | /api/warehouse/inventory-details/sync-all | JWT | 全量同步库存 |
| 库存总览 | GET | /api/inventory-overview | JWT | 库存总览 |
| 库存总览 | GET | /api/inventory-overview/warehouses | JWT | 仓库下拉选项 |
| 领星 | GET | /api/lingxing/token | API Key | 获取领星 Token |
| 领星 | GET | /api/lingxing/shops/ebay | API Key | eBay 店铺列表 |
| 领星 | POST | /api/lingxing/ebay/list | API Key | eBay 商品分页 |
| 领星 | POST | /api/lingxing/ebay/list/all | API Key | eBay 商品全量同步 |
| 领星 | GET | /api/lingxing/order/test | API Key | 测试订单查询 |
| 同步 | POST | /api/sync/all | JWT | 一键全量同步 |
| 同步 | POST | /api/sync/statement | JWT | 同步库存流水 |
| 同步 | POST | /api/sync/purchase-order | JWT | 同步采购订单 |
| 同步 | POST | /api/sync/purchase-order/init | JWT | 初始同步采购订单 |
| 同步 | POST | /api/sync/purchase-plan | JWT | 同步采购计划 |
| 同步 | POST | /api/sync/purchase-plan/init | JWT | 初始同步采购计划 |
| 谷仓 | POST | /api/goodcang/callback | 无 | Webhook 回调 |
| 谷仓 | GET | /api/goodcang/test/grn-list | 无 | 测试 GRN 列表 |
| 谷仓 | GET | /api/goodcang/test/grn-detail | 无 | 测试 GRN 详情 |
| 谷仓 | POST | /api/goodcang/sync-warehouse | 无 | 同步谷仓仓库 |
| 谷仓 | POST | /api/goodcang/sync-grn | 无 | 同步谷仓 GRN |

| 利润报表 | POST | /api/profit-report/upload | JWT | 上传利润报表 |
| 采购计划 | POST | /api/purchase-plan/upload | JWT | 上传采购计划 Excel |
| 采购计划 | POST | /api/purchase-plan/create | JWT | 创建采购计划 JSON |
| 采购计划 | GET | /api/purchase-plan/skus | JWT | SKU 搜索 |
| 采购计划 | GET | /api/purchase-plan/stores | JWT | 店铺搜索 |
| 采购计划 | GET | /api/purchase-plan/warehouses | JWT | 仓库搜索 |
| eBay 销量 | POST | /api/ebay-sales/upload | JWT | 上传 eBay 销量 |

---

> 文档生成日期：2026-05-28 | 基于 `openapi-sdk-java` 源码分析
