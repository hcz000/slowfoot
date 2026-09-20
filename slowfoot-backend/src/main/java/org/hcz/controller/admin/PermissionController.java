package org.hcz.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;


import org.hcz.security.access.Auth;
import org.hcz.entity.user.Permission;
import org.hcz.util.UserHolder;
import org.hcz.service.user.PermissionService;
import org.hcz.util.ApiResponse;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;


// 前端控制器
//
// 权限

@RestController
@RequestMapping("/authorize/permission")
public class PermissionController {

    @Resource
    private PermissionService permissionService;


// 权限列表
// @return

    @GetMapping("/list")
    @Auth("permission:list")
    public List<Permission> list(){
        return permissionService.list(Wrappers.emptyWrapper());
    }


// 新增权限时树形结构
// @return

    @GetMapping("/treeSelect")
    @Auth("permission:treeSelect")
    public List<Permission> treeSelect(){
        List<Permission> data = permissionService.treeSelect();

        return data;
    }


// 添加权限
// @param permission
// @return

    @PostMapping
    @Auth("permission:add")
    public ApiResponse add(@RequestBody Permission permission){
        permission.setIcon("fa "+permission.getIcon());
        permissionService.save(permission);

        return ApiResponse.ok();
    }


// 修改权限
// @param permission
// @return

    @PutMapping
    @Auth("permission:update")
    public ApiResponse update(@RequestBody Permission permission){
        permission.setIcon("fa "+permission.getIcon());
        permissionService.updateById(permission);
        return ApiResponse.ok();

    }


// 删除权限
// @param id
// @return

    @DeleteMapping("/{id}")
    @Auth("permission:delete")
    public ApiResponse delete(@PathVariable Long id){
        permissionService.removeMenu(id);
        return ApiResponse.ok().message("删除成功");
    }


// 初始化菜单
// @return

    @GetMapping("/initMenu")
    public Map<String, Object> initMenu(){
        Map<String, Object> data = permissionService.initMenu(UserHolder.get());
        return data;
    }
}
