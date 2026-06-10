package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.request.UserCreateRequest;
import com.asinking.com.openapi.dto.request.UserUpdateRequest;
import com.asinking.com.openapi.dto.response.UserResponse;
import com.asinking.com.openapi.interceptor.JwtAuthInterceptor;
import com.asinking.com.openapi.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 系统用户管理接口，支持用户的增删改查。
 * 操作者信息从 JWT token 中提取。
 */
@RestController
@RequestMapping("/api/users")
public class UserManageController {

    private final UserService userService;

    /** 构造器注入用户服务。 */
    public UserManageController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 创建用户。会自动创建关联的品牌归属记录。
     *
     * @param req account/密码/角色/品牌归属信息
     */
    @PostMapping
    public Result<UserResponse> create(@RequestBody UserCreateRequest req, HttpServletRequest request) {
        String operatorUserId = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));
        return Result.ok(userService.createUser(operatorUserId, req.getAccount(), req.getPassword(), req.getRole(), req.getOwnerName(), req.getBrandCode()));
    }

    /**
     * 根据 ID 更新用户。
     */
    @PutMapping("/{id}")
    public Result<UserResponse> update(@PathVariable String id, @RequestBody UserUpdateRequest req, HttpServletRequest request) {
        String operatorUserId = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));
        return Result.ok(userService.updateUser(operatorUserId, id, req.getRole(), req.getOwnerName(), req.getPassword()));
    }

    /**
     * 根据 ID 删除用户。
     */
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable String id, HttpServletRequest request) {
        String operatorUserId = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));
        boolean ok = userService.deleteUser(operatorUserId, id);
        return Result.ok(java.util.Collections.singletonMap("success", ok));
    }

    /**
     * 根据 ID 查询用户详情。
     */
    @GetMapping("/{id}")
    public Result<UserResponse> detail(@PathVariable String id) {
        return Result.ok(userService.getUserById(id));
    }

    /**
     * 分页查询用户列表，支持按 account / role 模糊搜索。
     */
    @GetMapping
    public Result<PageResult<UserResponse>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) String role) {
        return Result.ok(userService.pageUserResponses(page, size, account, role));
    }
}
