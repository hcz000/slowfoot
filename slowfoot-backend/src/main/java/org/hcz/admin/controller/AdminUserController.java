package org.hcz.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.hcz.admin.security.Auth;
import org.hcz.user.entity.Role;
import org.hcz.user.entity.User;
import org.hcz.user.entity.UserRole;
import org.hcz.common.vo.BasePage;
import org.hcz.user.service.RoleService;
import org.hcz.user.service.UserRoleService;
import org.hcz.user.service.UserService;
import org.hcz.common.util.ApiResponse;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;



@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    @Resource
    private UserService userService;

    @Resource
    private UserRoleService userRoleService;

    @Resource
    private RoleService roleService;

    @GetMapping("/list")
    @Auth("admin:user:list")
    public ApiResponse list(){
        return ApiResponse.ok().data(userService.list(new LambdaQueryWrapper<>()));
    }

    @GetMapping("/page")
    @Auth("admin:user:page")
    public ApiResponse list(BasePage basePage,
                  @RequestParam(required = false) String name,
                  @RequestParam(required = false) String email){
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>();
        wrapper.like(!ObjectUtils.isEmpty(name), User::getNickName, name)
                .like(!ObjectUtils.isEmpty(email), User::getEmail, email);
        IPage<User> page = userService.page(basePage.page(), wrapper);
        // 查出用户角色中间表
        Map<Long, List<UserRole>> userRoleMap = userRoleService.list(Wrappers.emptyWrapper()).stream().collect(Collectors.groupingBy(UserRole::getUserId));
        // 鏍规嵁瑙掕壊鏌ュ嚭瑙掕壊琛ㄤ俊鎭?
        Map<Long, String> roleMap = roleService.list(Wrappers.emptyWrapper()).stream().collect(Collectors.toMap(Role::getId, Role::getName));
        Map<Long, Set<String>> map = new HashMap<>();
        userRoleMap.forEach((uId,rIds)->{
            Set<String> roles = new HashSet<>();
            for (UserRole rId : rIds) {
                roles.add(roleMap.get(rId.getRoleId()));
            }
            map.put(uId,roles);
        });
        for (User user : page.getRecords()) {
            user.setRoleName(map.get(user.getId()));
        }
        return ApiResponse.ok().data(page.getRecords()).page(page.getCurrent(), page.getSize(), page.getTotal());
    }
}
