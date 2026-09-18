package org.hcz.service.user;

import org.hcz.entity.user.Role;
import com.baomidou.mybatisplus.extension.service.IService;
import org.hcz.entity.user.Tree;
import org.hcz.entity.vo.AssignRoleVO;
import org.hcz.entity.vo.AuthorityVO;

import java.util.List;



public interface RoleService extends IService<Role> {

    List<Tree> tree();

    void removeRole(String id);

    void gavePermission(AuthorityVO authorityVO);

    void gaveRole(AssignRoleVO assignRoleVO);

}
