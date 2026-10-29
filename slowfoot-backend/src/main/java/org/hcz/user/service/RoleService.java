package org.hcz.user.service;

import org.hcz.user.entity.Role;
import com.baomidou.mybatisplus.extension.service.IService;
import org.hcz.user.entity.Tree;
import org.hcz.admin.dto.AssignRoleVO;
import org.hcz.admin.dto.AuthorityVO;

import java.util.List;



public interface RoleService extends IService<Role> {

    List<Tree> tree();

    void removeRole(String id);

    void gavePermission(AuthorityVO authorityVO);

    void gaveRole(AssignRoleVO assignRoleVO);

}
