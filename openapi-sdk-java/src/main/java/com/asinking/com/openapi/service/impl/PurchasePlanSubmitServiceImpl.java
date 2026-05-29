package com.asinking.com.openapi.service.impl;

import com.asinking.com.openapi.common.response.PageResult;
import com.asinking.com.openapi.entity.PurchasePlanSubmitEntity;
import com.asinking.com.openapi.mapper.mp.PurchasePlanSubmitMapper;
import com.asinking.com.openapi.mapper.mp.TeamMapper;
import com.asinking.com.openapi.service.PurchasePlanSubmitService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PurchasePlanSubmitServiceImpl extends ServiceImpl<PurchasePlanSubmitMapper, PurchasePlanSubmitEntity>
        implements PurchasePlanSubmitService {

    private final TeamMapper teamMapper;

    public PurchasePlanSubmitServiceImpl(TeamMapper teamMapper) {
        this.teamMapper = teamMapper;
    }

    @Override
    public int batchSubmit(List<PurchasePlanSubmitEntity> items) {
        if (items == null || items.isEmpty()) return 0;
        for (PurchasePlanSubmitEntity e : items) {
            if (e.getId() == null || e.getId().isEmpty()) e.setId(uuid32());
        }
        saveBatch(items);
        return items.size();
    }

    @Override
    public PageResult<PurchasePlanSubmitEntity> page(long page, long size, String account, String role, String ownerName) {
        long p = page <= 0 ? 1 : page;
        long s = size <= 0 ? 10 : Math.min(size, 200);
        LambdaQueryWrapper<PurchasePlanSubmitEntity> wrapper = new LambdaQueryWrapper<>();

        if (!"admin".equalsIgnoreCase(role != null ? role.trim() : "")) {
            // 查 team 表：当前用户是否是组长
            Set<String> visibleOwners = new HashSet<>();
            visibleOwners.add(ownerName); // 至少能看到自己同 owner 的

            List<String> members = teamMapper.selectList(
                    new LambdaQueryWrapper<com.asinking.com.openapi.entity.TeamEntity>()
                            .eq(com.asinking.com.openapi.entity.TeamEntity::getLeader, ownerName))
                    .stream().map(t -> t.getMember()).collect(Collectors.toList());
            visibleOwners.addAll(members);

            wrapper.in(PurchasePlanSubmitEntity::getCreatorOwnerName, visibleOwners);
        }

        wrapper.orderByDesc(PurchasePlanSubmitEntity::getSubmitTime);
        Page<PurchasePlanSubmitEntity> mpPage = new Page<>(p, s);
        Page<PurchasePlanSubmitEntity> result = page(mpPage, wrapper);
        List<PurchasePlanSubmitEntity> records = result.getRecords() == null ? Collections.emptyList() : result.getRecords();
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    private String uuid32() { return UUID.randomUUID().toString().replace("-", ""); }
}
