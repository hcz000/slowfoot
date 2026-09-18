package org.hcz.service.user.impl;

import org.hcz.entity.user.UserRole;
import org.hcz.mapper.user.UserRoleMapper;
import org.hcz.service.user.UserRoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;



@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {

}
