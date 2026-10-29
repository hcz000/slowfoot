package org.hcz.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.user.entity.UserSubscribe;
import org.hcz.user.mapper.UserSubscribeMapper;
import org.hcz.user.service.UserSubscribeService;
import org.springframework.stereotype.Service;



@Service
public class UserSubscribeServiceImpl  extends ServiceImpl<UserSubscribeMapper, UserSubscribe> implements UserSubscribeService {
}
