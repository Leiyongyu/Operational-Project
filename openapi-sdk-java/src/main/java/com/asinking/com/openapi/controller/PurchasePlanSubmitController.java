package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.entity.PurchasePlanSubmitEntity;
import com.asinking.com.openapi.entity.UserEntity;
import com.asinking.com.openapi.entity.WarehouseEntity;
import com.asinking.com.openapi.interceptor.JwtAuthInterceptor;
import com.asinking.com.openapi.service.PurchasePlanSubmitService;
import com.asinking.com.openapi.service.UserService;
import com.asinking.com.openapi.service.WarehouseService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/purchase-plan-submit")
public class PurchasePlanSubmitController {

    private final PurchasePlanSubmitService service;
    private final UserService userService;
    private final WarehouseService warehouseService;

    public PurchasePlanSubmitController(PurchasePlanSubmitService service, UserService userService,
                                        WarehouseService warehouseService) {
        this.service = service;
        this.userService = userService;
        this.warehouseService = warehouseService;
    }

    /** 批量提交采购计划，自动记录当前登录用户信息和仓库名。 */
    @PostMapping
    public Result<Integer> submit(@RequestBody List<PurchasePlanSubmitEntity> items,
                                  HttpServletRequest request) {
        String account = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ACCOUNT));
        String role = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ROLE));
        String ownerName = resolveOwnerName(account);
        for (PurchasePlanSubmitEntity e : items) {
            e.setCreatorAccount(account);
            e.setCreatorRole(role);
            e.setCreatorOwnerName(ownerName);
            // 自动填充仓库名
            if (e.getWid() != null && (e.getWarehouseName() == null || e.getWarehouseName().isEmpty())) {
                WarehouseEntity wh = warehouseService.lambdaQuery()
                        .eq(WarehouseEntity::getWid, e.getWid()).one();
                if (wh != null) e.setWarehouseName(wh.getName());
            }
        }
        return Result.ok(service.batchSubmit(items));
    }

    /** 分页查询，支持 SKU 和创建人模糊搜索 */
    @GetMapping
    public Result<PageResult<PurchasePlanSubmitEntity>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String creator,
            HttpServletRequest request) {
        String account = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ACCOUNT));
        String role = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ROLE));
        String ownerName = resolveOwnerName(account);
        return Result.ok(service.page(page, size, account, role, ownerName, sku, creator));
    }

    /** 更新采购计划（仅 quantity_plan） */
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id,
                                              @RequestBody Map<String, Object> body) {
        PurchasePlanSubmitEntity e = service.getById(id);
        if (e == null) return Result.fail(
                com.asinking.com.openapi.common.response.ResultCode.BAD_REQUEST, "记录不存在");
        if (body.containsKey("quantityPlan")) {
            e.setQuantityPlan(Integer.parseInt(String.valueOf(body.get("quantityPlan"))));
        }
        if (body.containsKey("quantityReplenish")) {
            e.setQuantityReplenish(Integer.parseInt(String.valueOf(body.get("quantityReplenish"))));
        }
        if (body.containsKey("remark")) {
            e.setRemark(String.valueOf(body.get("remark")));
        }
        service.updateById(e);
        return Result.ok(Collections.singletonMap("success", true));
    }

    /** 删除单条记录 */
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable String id) {
        service.removeById(id);
        return Result.ok(Collections.singletonMap("success", true));
    }

    /** 批量更新状态，审批时记录审批人和时间 */
    @PutMapping("/batch-status")
    public Result<Integer> batchStatus(@RequestBody Map<String, Object> body,
                                       HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        String status = (String) body.get("status");
        if (ids == null || ids.isEmpty() || status == null) return Result.fail(
                com.asinking.com.openapi.common.response.ResultCode.BAD_REQUEST, "ids 和 status 不能为空");

        String currentUser = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ACCOUNT));
        String approverName = resolveOwnerName(currentUser);
        // 如果 ownerName 为空，用账号兜底
        if (approverName.isEmpty()) approverName = currentUser;
        int count = 0;
        for (String id : ids) {
            PurchasePlanSubmitEntity e = service.getById(id);
            if (e != null) {
                e.setStatusText(status);
                if ("已审批".equals(status) || "已驳回".equals(status)) {
                    e.setApprover(approverName);
                    e.setApproveTime(java.time.LocalDateTime.now());
                }
                service.updateById(e);
                count++;
            }
        }
        return Result.ok(count);
    }

    /** 导出当前筛选数据为 Excel */
    @GetMapping("/export")
    public void export(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String account = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ACCOUNT));
        String role = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ROLE));
        String ownerName = resolveOwnerName(account);

        List<PurchasePlanSubmitEntity> records = service.list();

        // 权限过滤（管理员看全部，组长看组员，组员看自己）
        if (!"admin".equalsIgnoreCase(role != null ? role.trim() : "")) {
            Set<String> allowed = new HashSet<>();
            allowed.add(ownerName);
            records = records.stream()
                    .filter(r -> allowed.contains(r.getCreatorOwnerName()))
                    .collect(java.util.stream.Collectors.toList());
        }

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("采购计划");
        Row header = sheet.createRow(0);
        String[] headers = {"*SKU", "店铺", "FNSKU", "供应商", "单箱数量", "箱数", "仓库", "采购方",
                "计划采购量", "期望到货时间", "备注", "状态", "审批人", "审批时间"};
        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (PurchasePlanSubmitEntity e : records) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(e.getSku() != null ? e.getSku() : "");
            row.createCell(1).setCellValue(e.getSid() != null ? e.getSid() : "");
            row.createCell(2).setCellValue(e.getFnsku() != null ? e.getFnsku() : "");
            row.createCell(3).setCellValue(e.getSupplierId() != null ? String.valueOf(e.getSupplierId()) : "");
            row.createCell(4).setCellValue("");
            row.createCell(5).setCellValue("");
            row.createCell(6).setCellValue(e.getWarehouseName() != null ? e.getWarehouseName() : "");
            row.createCell(7).setCellValue(e.getPurchaserId() != null ? String.valueOf(e.getPurchaserId()) : "");
            row.createCell(8).setCellValue(e.getQuantityPlan() != null ? e.getQuantityPlan() : 0);
            row.createCell(9).setCellValue(e.getExpectArriveTime() != null ? e.getExpectArriveTime() : "");
            row.createCell(10).setCellValue(e.getRemark() != null ? e.getRemark() : "");
            row.createCell(11).setCellValue(e.getStatusText() != null ? e.getStatusText() : "已提交");
            row.createCell(12).setCellValue(e.getApprover() != null ? e.getApprover() : "");
            row.createCell(13).setCellValue(e.getApproveTime() != null
                    ? e.getApproveTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" +
                URLEncoder.encode("采购计划导出.xlsx", StandardCharsets.UTF_8.toString()));
        OutputStream os = response.getOutputStream();
        wb.write(os);
        wb.close();
        os.flush();
    }

    private String resolveOwnerName(String account) {
        if (account == null || account.isEmpty() || "null".equals(account)) return "";
        UserEntity user = userService.lambdaQuery()
                .eq(UserEntity::getAccount, account).one();
        String owner = user != null && user.getOwnerName() != null && !user.getOwnerName().trim().isEmpty()
                ? user.getOwnerName().trim() : "";
        // 如果 ownerName 为空，用 account 兜底
        return !owner.isEmpty() ? owner : account;
    }
}
