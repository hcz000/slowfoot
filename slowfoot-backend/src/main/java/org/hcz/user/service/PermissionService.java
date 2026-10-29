package org.hcz.user.service;

import org.hcz.user.entity.Permission;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;



public interface PermissionService extends IService<Permission> {

    Map<String, Object> initMenu(Long userId);


    List<Permission> treeSelect();


    void removeMenu(Long id);
}
