package org.hcz.service.user.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.authority.AuthStore;
import org.hcz.entity.user.Role;
import org.hcz.entity.user.RolePermission;
import org.hcz.entity.user.Tree;
import org.hcz.entity.user.UserRole;
import org.hcz.entity.vo.AssignRoleVO;
import org.hcz.entity.vo.AuthorityVO;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.mapper.user.RoleMapper;
import org.hcz.service.user.PermissionService;
import org.hcz.service.user.RolePermissionService;
import org.hcz.service.user.RoleService;
import org.hcz.service.user.UserRoleService;
import org.hcz.service.user.*;
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
        Long roleId = Long.valueOf(id);
        List<Long> affectedUserIds = userRoleService.list(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, roleId).select(UserRole::getUserId))
                .stream().map(UserRole::getUserId).toList();
        try{
            // 删除角色权限中间表
            rolePermissionService.remove(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId,id));
            // 删除角色表
            baseMapper.deleteById(id);
            // 删除用户角色表
            userRoleService.remove(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId,id));
            affectedUserIds.forEach(AuthStore::remove);
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
            userRoleService.list(new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getRoleId, rid.longValue()).select(UserRole::getUserId))
                    .stream().map(UserRole::getUserId).forEach(AuthStore::remove);
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
            AuthStore.remove(uId);
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
