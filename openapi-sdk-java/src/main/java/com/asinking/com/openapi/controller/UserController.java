package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.dto.request.UserLoginRequest;
import com.asinking.com.openapi.dto.response.UserLoginResponse;
import com.asinking.com.openapi.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 用户认证接口，处理登录和登出。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    /** 构造器注入用户服务。 */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户登录，校验账号密码后返回 JWT token。
     *
     * @param req account 账号, password 密码
     */
    @PostMapping("/login")
    public Result<UserLoginResponse> login(@RequestBody UserLoginRequest req) {
        return Result.ok(userService.login(req.getAccount(), req.getPassword()));
    }

    /**
     * 用户登出，将当前 token 加入黑名单使之失效。
     *
     * @param authorization 请求头 Authorization: Bearer <token>
     */
    @PostMapping("/logout")
    public Result<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.logout(authorization);
        return Result.ok(Collections.singletonMap("success", true));
    }
}
