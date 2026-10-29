package org.hcz.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.admin.security.AuthStore;
import org.hcz.user.entity.Role;
import org.hcz.user.entity.RolePermission;
import org.hcz.user.entity.Tree;
import org.hcz.user.entity.UserRole;
import org.hcz.admin.dto.AssignRoleVO;
import org.hcz.admin.dto.AuthorityVO;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.user.mapper.RoleMapper;
import org.hcz.user.service.PermissionService;
import org.hcz.user.service.RolePermissionService;
import org.hcz.user.service.RoleService;
import org.hcz.user.service.UserRoleService;
import org.hcz.user.service.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;



@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {


    @Resource
    private PermissionService permissionService;

    @Autowired
    private RolePermissionService rolePermissionService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private AuthStore authStore;


    @Override
    public List<Tree> tree() {
        List<Tree> trees = permissionService.list(Wrappers.emptyWrapper()).stream().map(permission -> {

            Tree tree = new Tree();
            BeanUtils.copyProperties(permission, tree);
            tree.setTitle(permission.getName());
            tree.setSpread(true);
            return tree;
        }).collect(Collectors.toList());


        // 找到根节点
        List<Tree> parent = trees.stream().filter(tree -> tree.getPId().compareTo(0L) == 0).collect(Collectors.toList());
        for (Tree item : parent) {
            findChildren(item, trees);
        }

        return parent;
    }


    @Override
    @Transactional
    public void removeRole(String id) {
        try{
            // 删除角色权限中间表
            rolePermissionService.remove(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId,id));
            // 删除角色表
            baseMapper.deleteById(id);
            // 删除用户角色表
            userRoleService.remove(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId,id));
            // 失效所有实例的本地权限快照
            authStore.invalidateAll();
        }catch (Exception e){
            throw new AppException(ErrorCode.INTERNAL_ERROR, "删除角色失败", e);
        }
    }

    @Override
    @Transactional
    public void gavePermission(AuthorityVO authorityVO) {
        try{
            rolePermissionService.remove(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId,authorityVO.getRid()));
            List<RolePermission> list = new ArrayList<>();
            Integer rid = authorityVO.getRid();
            for (Integer pId : authorityVO.getPid()) {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(rid);
                rolePermission.setPermissionId(pId);
                list.add(rolePermission);
            }
            rolePermissionService.saveBatch(list);
            // 失效所有实例的本地权限快照
            authStore.invalidateAll();
        }catch (Exception e){
            throw new AppException(ErrorCode.INTERNAL_ERROR, "分配权限失败", e);
        }
    }

    @Override
    @Transactional
    public void gaveRole(AssignRoleVO assignRoleVO) {
        // 获取被分配角色的用户信息
        Long uId = assignRoleVO.getUId();
        try{
            userRoleService.remove(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId,uId));
            List<UserRole> userRoles = new ArrayList<>();
            for (Long id : assignRoleVO.getRId()) {
                UserRole userRole = new UserRole();
                userRole.setUserId(uId);
                userRole.setRoleId(id);
                userRoles.add(userRole);
            }
            userRoleService.saveBatch(userRoles);
            // 失效所有实例的本地权限快照
            authStore.invalidateAll();
        }catch (Exception e){
            throw new AppException(ErrorCode.INTERNAL_ERROR, "分配角色失败", e);
        }
    }

    private Tree findChildren(Tree datum, List<Tree> trees) {
        datum.setChildren(new ArrayList<Tree>());
        for (Tree tree : trees) {
            if (tree.getPId().compareTo(datum.getId()) == 0) {
                datum.getChildren().add(findChildren(tree,trees));
            }
        }
        return datum;
    }
}
