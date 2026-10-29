package org.hcz.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.hcz.admin.security.Auth;
import org.hcz.user.entity.Role;
import org.hcz.user.entity.RolePermission;
import org.hcz.user.entity.Tree;
import org.hcz.user.entity.UserRole;
import org.hcz.admin.dto.AssignRoleVO;
import org.hcz.admin.dto.AuthorityVO;
import org.hcz.common.vo.BasePage;
import org.hcz.user.service.RolePermissionService;
import org.hcz.user.service.RoleService;
import org.hcz.user.service.UserRoleService;
import org.hcz.common.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


// 前端控制器
//

@RestController
@RequestMapping("/authorize/role")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @Autowired
    private RolePermissionService rolePermissionService;

    @Autowired
    private UserRoleService userRoleService;


    @GetMapping("/treeList")
    @Auth("permission:treeList")
    public List<Tree> treeList(){
        List<Tree> data = roleService.tree();
        return data;
    }

    @PostMapping("/assignRole")
    @Auth("user:assignRole")
    public ApiResponse assignRole(@RequestBody AssignRoleVO assignRoleVO){
        roleService.gaveRole(assignRoleVO);
        return ApiResponse.ok().message("分配角色成功");
    }


    @GetMapping("/getUserRole/{userId}")
    @Auth("role:getRole")
    public List getRole(@PathVariable Integer userId){
        List<Long> list = userRoleService.list(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId,userId).select(UserRole::getRoleId))
                .stream().map(UserRole::getRoleId).collect(Collectors.toList());
        return list;
    }


// 初始化角色
// @return

    @GetMapping("/initRole")
    @Auth("role:initRole")
    public List<Map<String, Object>> initRole(){
        // 查出所有角色
        List<Map<String, Object>> list = roleService.list(Wrappers.emptyWrapper()).stream()
                .map(role -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("value", role.getId());
                    data.put("title", role.getName());
                    return data;
                }).collect(Collectors.toList());

        return list;
    }


    @GetMapping("/list")
    @Auth("role:list")
    public ApiResponse list(BasePage basePage, @RequestParam(required = false) String name){
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(!ObjectUtils.isEmpty(name),Role::getName,name);
        final IPage<Role> iPage = basePage.page();
        IPage<Role> page = roleService.page(iPage,wrapper);
        return ApiResponse.ok().data(page.getRecords()).page(page.getCurrent(), page.getSize(), page.getTotal());
    }


// 添加角色
// @param role
// @return

    @PostMapping
    @Auth("role:add")
    public ApiResponse add(@RequestBody Role role){
        roleService.save(role);
        return ApiResponse.ok();
    }


// 修改角色
// @param role
// @return

    @PutMapping
    @Auth("role:update")
    public ApiResponse update(@RequestBody Role role){
        roleService.updateById(role);
        return ApiResponse.ok();
    }


// 删除角色
// @param id
// @return

    @DeleteMapping("/{id}")
    @Auth("role:delete")
    public ApiResponse delete(@PathVariable String id){
        roleService.removeRole(id);
        return ApiResponse.ok().message("删除成功");
    }


// 给角色分配权限
// @param authorityVO
// @return
// 给角色分配权限前先把该角色的权限都删了

    @PostMapping("/authority")
    @Auth("role:authority")
    public ApiResponse authority(@RequestBody AuthorityVO authorityVO){
        roleService.gavePermission(authorityVO);
        return ApiResponse.ok().message("分配权限成功");
    }


// 获取角色权限
// @param id
// @return

    @GetMapping("/getPermission/{id}")
    @Auth("role:getPermission")
    public Integer[] getPermission(@PathVariable Integer id){
        Integer[] list = rolePermissionService.list(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId,id).select(RolePermission::getPermissionId))
                .stream().map(RolePermission::getPermissionId).toArray(Integer[]::new);
        return list;
    }

}
