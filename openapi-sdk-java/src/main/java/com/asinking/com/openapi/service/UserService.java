package com.asinking.com.openapi.service;

import com.asinking.com.openapi.dto.response.UserLoginResponse;
import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.dto.response.UserResponse;
import com.asinking.com.openapi.entity.UserEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 用户业务接口，管理用户认证和 CRUD。
 */
public interface UserService extends IService<UserEntity> {

    /** 用户登录，验证账号密码后签发 JWT。 */
    UserLoginResponse login(String account, String password);

    /** 用户登出，将 token 加入黑名单。 */
    void logout(String authorizationHeader);

    /** 创建新用户，关联品牌归属。 */
    UserResponse createUser(String operatorUserId, String account, String password, String role, String ownerName, String brandCode);

    /** 更新用户信息（角色、负责人、密码）。 */
    UserResponse updateUser(String operatorUserId, String id, String role, String ownerName, String password);

    /** 根据 ID 删除用户。 */
    boolean deleteUser(String operatorUserId, String id);

    /** 根据 ID 查询用户。 */
    UserResponse getUserById(String id);

    /** 分页查询用户（返回 UserEntity）。 */
    Page<UserEntity> pageUsers(long page, long size, String account, String role);

    /** 分页查询用户（返回 UserResponse，含品牌归属信息）。 */
    PageResult<UserResponse> pageUserResponses(long page, long size, String account, String role);
}
