package com.asinking.com.openapi.controller;

import com.asinking.com.openapi.common.response.Result;
import com.asinking.com.openapi.common.response.ResultCode;
import com.asinking.com.openapi.entity.BrandOwnerEntity;
import com.asinking.com.openapi.entity.TeamEntity;
import com.asinking.com.openapi.mapper.mp.TeamMapper;
import com.asinking.com.openapi.service.BrandOwnerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 团队管理接口：组长/组员 CRUD。
 */
@RestController
@RequestMapping("/api/team")
public class TeamController {

    private final TeamMapper teamMapper;
    private final BrandOwnerService brandOwnerService;

    public TeamController(TeamMapper teamMapper, BrandOwnerService brandOwnerService) {
        this.teamMapper = teamMapper;
        this.brandOwnerService = brandOwnerService;
    }

    /** 获取某个组长的所有组员 */
    @GetMapping("/members")
    public Result<List<String>> members(@RequestParam String leader) {
        List<String> list = teamMapper.selectList(
                new LambdaQueryWrapper<TeamEntity>().eq(TeamEntity::getLeader, leader))
                .stream().map(TeamEntity::getMember).collect(Collectors.toList());
        return Result.ok(list);
    }

    /** 添加组员 */
    @PostMapping
    public Result<Map<String, Object>> addMember(@RequestBody Map<String, String> body) {
        String leader = body.get("leader");
        String member = body.get("member");
        if (leader == null || leader.isEmpty() || member == null || member.isEmpty())
            return Result.fail(ResultCode.BAD_REQUEST,"leader 和 member 不能为空");

        // 不能添加已经是组长的人
        boolean isLeader = teamMapper.selectCount(
                new LambdaQueryWrapper<TeamEntity>().eq(TeamEntity::getLeader, member)) > 0;
        if (isLeader) return Result.fail(ResultCode.BAD_REQUEST,"该成员已是组长，不能添加");

        // 检查是否已存在
        boolean exists = teamMapper.selectCount(
                new LambdaQueryWrapper<TeamEntity>()
                        .eq(TeamEntity::getLeader, leader)
                        .eq(TeamEntity::getMember, member)) > 0;
        if (exists) return Result.fail(ResultCode.BAD_REQUEST,"已存在该组员");

        TeamEntity e = new TeamEntity();
        e.setLeader(leader);
        e.setMember(member);
        teamMapper.insert(e);

        Map<String, Object> r = new HashMap<>();
        r.put("id", e.getId());
        return Result.ok(r);
    }

    /** 移除组员（按 leader + member） */
    @DeleteMapping
    public Result<Map<String, Object>> removeMember(@RequestParam String leader, @RequestParam String member) {
        teamMapper.delete(new LambdaQueryWrapper<TeamEntity>()
                .eq(TeamEntity::getLeader, leader)
                .eq(TeamEntity::getMember, member));
        return Result.ok(Collections.singletonMap("success", true));
    }

    /** 设置组长（自引用标记） */
    @PostMapping("/set-leader")
    public Result<Map<String, Object>> setLeader(@RequestBody Map<String, String> body) {
        String ownerName = body.get("ownerName");
        if (ownerName == null || ownerName.isEmpty()) return Result.fail(ResultCode.BAD_REQUEST,"ownerName 不能为空");

        boolean exists = teamMapper.selectCount(
                new LambdaQueryWrapper<TeamEntity>()
                        .eq(TeamEntity::getLeader, ownerName)
                        .eq(TeamEntity::getMember, ownerName)) > 0;
        if (!exists) {
            TeamEntity e = new TeamEntity();
            e.setLeader(ownerName);
            e.setMember(ownerName);
            teamMapper.insert(e);
        }
        return Result.ok(Collections.singletonMap("success", true));
    }

    /** 取消组长 */
    @DeleteMapping("/leader")
    public Result<Map<String, Object>> removeLeader(@RequestParam String leader) {
        teamMapper.delete(new LambdaQueryWrapper<TeamEntity>().eq(TeamEntity::getLeader, leader));
        return Result.ok(Collections.singletonMap("success", true));
    }

    /** 获取全部团队关系（leader → members 映射） */
    @GetMapping("/all")
    public Result<Map<String, List<String>>> allTeams() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (TeamEntity e : teamMapper.selectList(null)) {
            if (e.getLeader() == null || e.getMember() == null) continue;
            // 跳过自引用（leader == member）
            if (e.getLeader().equals(e.getMember())) continue;
            result.computeIfAbsent(e.getLeader(), k -> new ArrayList<>()).add(e.getMember());
        }
        return Result.ok(result);
    }

    /** 检查某人是否是组长 */
    @GetMapping("/is-leader")
    public Result<Boolean> isLeader(@RequestParam String ownerName) {
        boolean isLeader = teamMapper.selectCount(
                new LambdaQueryWrapper<TeamEntity>().eq(TeamEntity::getLeader, ownerName)) > 0;
        return Result.ok(isLeader);
    }

    /** 可添加为组员的人员列表（来自 brand_owner 去重，排除已是组长的人） */
    @GetMapping("/available-members")
    public Result<List<String>> availableMembers(@RequestParam(required = false) String excludeLeader) {
        // 所有已是组长的人
        Set<String> leaders = teamMapper.selectList(null).stream()
                .map(TeamEntity::getLeader).filter(Objects::nonNull).collect(Collectors.toSet());

        // brand_owner 去重 owner_name
        List<String> all = brandOwnerService.list().stream()
                .map(BrandOwnerEntity::getOwnerName)
                .filter(n -> n != null && !n.isEmpty())
                .distinct().sorted()
                .collect(Collectors.toList());

        // 排除已是组长的，排除当前组长自己
        List<String> available = all.stream()
                .filter(n -> !leaders.contains(n))
                .filter(n -> excludeLeader == null || !n.equals(excludeLeader))
                .collect(Collectors.toList());

        return Result.ok(available);
    }
}
