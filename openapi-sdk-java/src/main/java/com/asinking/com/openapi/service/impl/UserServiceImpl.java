package com.asinking.com.openapi.service.impl;

import com.asinking.com.openapi.common.exception.BusinessException;
import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.common.response.ResultCode;
import com.asinking.com.openapi.config.TokenBlacklist;
import com.asinking.com.openapi.dto.response.UserLoginResponse;
import com.asinking.com.openapi.dto.response.UserResponse;
import com.asinking.com.openapi.entity.BrandOwnerEntity;
import com.asinking.com.openapi.entity.UserEntity;
import com.asinking.com.openapi.mapper.mp.UserMapper;
import com.asinking.com.openapi.service.BrandOwnerService;
import com.asinking.com.openapi.service.UserService;
import com.asinking.com.openapi.utils.JwtTokenService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    private static final Pattern MD5_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklist tokenBlacklist;
    private final BrandOwnerService brandOwnerService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(JwtTokenService jwtTokenService, TokenBlacklist tokenBlacklist, BrandOwnerService brandOwnerService) {
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklist = tokenBlacklist;
        this.brandOwnerService = brandOwnerService;
    }

    @Override
    public UserLoginResponse login(String account, String password) {
        if (!StringUtils.hasText(account) || !StringUtils.hasText(password)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "账号或密码不能为空");
        }

        UserEntity user = lambdaQuery()
                .eq(UserEntity::getAccount, account)
                .last("limit 1")
                .one();
        if (user == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "账号或密码错误");
        }

        if (!matches(password, user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "账号或密码错误");
        }

        JwtTokenService.TokenInfo tokenInfo = jwtTokenService.issue(user);
        return new UserLoginResponse(tokenInfo.getToken(), tokenInfo.getExpiresAtMillis(), user.getAccount(), user.getRole(), user.getOwnerName());
    }

    @Override
    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Missing token");
        }

        Jws<Claims> jws = jwtTokenService.parse(token);
        Claims claims = jws.getBody();
        String jti = claims.getId();
        long expMillis = claims.getExpiration() != null ? claims.getExpiration().getTime() : System.currentTimeMillis();
        tokenBlacklist.revoke(jti, expMillis);
    }

    @Override
    @Transactional
    public UserResponse createUser(String operatorUserId, String account, String password, String role, String ownerName, String brandCode) {
        if (!StringUtils.hasText(account)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "account 不能为空");
        }
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "password 不能为空");
        }
        if (!StringUtils.hasText(role)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "role 不能为空");
        }

        if (!StringUtils.hasText(brandCode)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "brandCode 不能为空");
        }
        if (!StringUtils.hasText(ownerName)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "ownerName 不能为空");
        }

        String trimmedBrandCode = brandCode.trim();
        String trimmedOwnerName = ownerName.trim();

        boolean brandExists = brandOwnerService.lambdaQuery()
                .eq(BrandOwnerEntity::getBrandCode, trimmedBrandCode)
                .exists();
        if (!brandExists) {
            BrandOwnerEntity brandOwnerEntity = new BrandOwnerEntity();
            brandOwnerEntity.setBrandCode(trimmedBrandCode);
            brandOwnerEntity.setOwnerName(trimmedOwnerName);
            try {
                brandOwnerService.save(brandOwnerEntity);
            } catch (DuplicateKeyException ignore) {
            }
        }

        boolean exists = lambdaQuery().eq(UserEntity::getAccount, account).exists();
        if (exists) {
            throw new BusinessException(ResultCode.CONFLICT, "账号已存在");
        }

        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setAccount(account.trim());
        entity.setPassword(passwordEncoder.encode(password));
        entity.setRole(role.trim());
        entity.setOwnerName(trimmedOwnerName);

        boolean ok = save(entity);
        if (!ok) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "创建用户失败");
        }
        return toResponse(entity);
    }

    @Override
    public UserResponse updateUser(String operatorUserId, String id, String role, String ownerName, String password) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "id 不能为空");
        }

        UserEntity entity = getById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户不存在");
        }

        if (StringUtils.hasText(role)) {
            entity.setRole(role.trim());
        }
        if (ownerName != null) {
            if (!StringUtils.hasText(ownerName)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "ownerName 不能为空");
            }
            entity.setOwnerName(ownerName.trim());
        }
        if (StringUtils.hasText(password)) {
            entity.setPassword(passwordEncoder.encode(password));
        }

        boolean ok = updateById(entity);
        if (!ok) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "更新用户失败");
        }
        return toResponse(entity);
    }

    @Override
    public boolean deleteUser(String operatorUserId, String id) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "id 不能为空");
        }
        return removeById(id);
    }

    @Override
    public UserResponse getUserById(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "id 不能为空");
        }
        UserEntity entity = getById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户不存在");
        }
        return toResponse(entity);
    }

    @Override
    public Page<UserEntity> pageUsers(long page, long size, String account, String role) {
        long p = page <= 0 ? 1 : page;
        long s = size <= 0 ? 10 : Math.min(size, 200);
        Page<UserEntity> mpPage = new Page<>(p, s);
        return page(mpPage, lambdaQuery()
                .like(StringUtils.hasText(account), UserEntity::getAccount, account)
                .eq(StringUtils.hasText(role), UserEntity::getRole, role)
                .getWrapper());
    }

    @Override
    public PageResult<UserResponse> pageUserResponses(long page, long size, String account, String role) {
        Page<UserEntity> result = pageUsers(page, size, account, role);
        List<UserEntity> records = result.getRecords() == null ? Collections.emptyList() : result.getRecords();
        Map<String, BrandOwnerEntity> brandOwnerIndex = buildBrandOwnerIndex(records);
        List<UserResponse> responses = new ArrayList<>(records.size());
        for (UserEntity user : records) {
            responses.add(toResponse(user, brandOwnerIndex));
        }
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), responses);
    }

    private UserResponse toResponse(UserEntity entity) {
        return toResponse(entity, buildBrandOwnerIndex(Collections.singletonList(entity)));
    }

    private UserResponse toResponse(UserEntity entity, Map<String, BrandOwnerEntity> brandOwnerIndex) {
        UserResponse resp = new UserResponse();
        resp.setId(entity.getId());
        resp.setAccount(entity.getAccount());
        resp.setRole(entity.getRole());
        resp.setOwnerName(entity.getOwnerName());
        resp.setOwners(resolveOwnerBrands(entity.getOwnerName()));
        resp.setCreateTime(entity.getCreateTime());
        resp.setUpdateTime(entity.getUpdateTime());
        return resp;
    }

    private Map<String, BrandOwnerEntity> buildBrandOwnerIndex(List<UserEntity> users) {
        Set<String> ownerNames = new HashSet<>();
        for (UserEntity user : users) {
            if (user != null && StringUtils.hasText(user.getOwnerName())) {
                ownerNames.add(user.getOwnerName().trim());
            }
        }
        if (ownerNames.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> nameList = new ArrayList<>(ownerNames);
        List<BrandOwnerEntity> owners = brandOwnerService.lambdaQuery()
                .in(BrandOwnerEntity::getOwnerName, nameList)
                .list();

        Map<String, BrandOwnerEntity> index = new HashMap<>();
        if (owners != null) {
            for (BrandOwnerEntity owner : owners) {
                if (owner != null && StringUtils.hasText(owner.getBrandCode())) {
                    index.put(owner.getBrandCode(), owner);
                }
            }
        }
        return index;
    }

    /** 根据 owner_name 查 brand_owner 表，返回该负责人名下的所有品牌代码列表 */
    private List<String> resolveOwnerBrands(String ownerName) {
        if (!StringUtils.hasText(ownerName)) {
            return Collections.emptyList();
        }
        return brandOwnerService.lambdaQuery()
                .eq(BrandOwnerEntity::getOwnerName, ownerName.trim())
                .list()
                .stream()
                .filter(bo -> StringUtils.hasText(bo.getBrandCode()))
                .map(BrandOwnerEntity::getBrandCode)
                .collect(Collectors.toList());
    }

    private boolean matches(String raw, String stored) {
        if (!StringUtils.hasText(stored)) {
            return false;
        }
        String trimmed = stored.trim();
        if (trimmed.startsWith("$2a$") || trimmed.startsWith("$2b$") || trimmed.startsWith("$2y$")) {
            return passwordEncoder.matches(raw, trimmed);
        }
        if (MD5_PATTERN.matcher(trimmed).matches()) {
            return DigestUtils.md5Hex(raw).equalsIgnoreCase(trimmed);
        }
        return raw.equals(trimmed);
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }
}
