package org.hcz.service.user.impl;

import org.hcz.entity.user.RolePermission;
import org.hcz.mapper.user.RolePermissionMapper;
import org.hcz.service.user.RolePermissionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;



@Service
public class RolePermissionServiceImpl extends ServiceImpl<RolePermissionMapper, RolePermission> implements RolePermissionService {

}
