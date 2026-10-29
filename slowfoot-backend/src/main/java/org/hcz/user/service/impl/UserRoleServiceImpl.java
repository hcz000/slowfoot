package org.hcz.user.service.impl;

import org.hcz.user.entity.UserRole;
import org.hcz.user.mapper.UserRoleMapper;
import org.hcz.user.service.UserRoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;



@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {

}
