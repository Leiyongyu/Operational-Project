package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.entity.UserColumnConfigEntity;
import com.asinking.com.openapi.interceptor.JwtAuthInterceptor;
import com.asinking.com.openapi.mapper.mp.UserColumnConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/user-column-config")
public class UserColumnConfigController {

    private final UserColumnConfigMapper mapper;

    public UserColumnConfigController(UserColumnConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 获取当前用户指定页面的列配置 */
    @GetMapping
    public Result<String> get(@RequestParam(defaultValue = "dashboard") String pageKey,
                              HttpServletRequest request) {
        String account = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ACCOUNT));
        UserColumnConfigEntity e = mapper.selectOne(new LambdaQueryWrapper<UserColumnConfigEntity>()
                .eq(UserColumnConfigEntity::getUserAccount, account)
                .eq(UserColumnConfigEntity::getPageKey, pageKey));
        return Result.ok(e != null ? e.getConfigJson() : null);
    }

    /** 保存当前用户指定页面的列配置 */
    @PostMapping
    public Result<Map<String, Object>> save(@RequestParam(defaultValue = "dashboard") String pageKey,
                                            @RequestBody String configJson,
                                            HttpServletRequest request) {
        String account = String.valueOf(request.getAttribute(JwtAuthInterceptor.ATTR_ACCOUNT));
        UserColumnConfigEntity exist = mapper.selectOne(new LambdaQueryWrapper<UserColumnConfigEntity>()
                .eq(UserColumnConfigEntity::getUserAccount, account)
                .eq(UserColumnConfigEntity::getPageKey, pageKey));
        if (exist != null) {
            exist.setConfigJson(configJson);
            mapper.updateById(exist);
        } else {
            UserColumnConfigEntity e = new UserColumnConfigEntity();
            e.setUserAccount(account);
            e.setPageKey(pageKey);
            e.setConfigJson(configJson);
            mapper.insert(e);
        }
        return Result.ok(Map.of("success", true));
    }
}
