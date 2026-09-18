package org.hcz.service.user.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.entity.user.UserSubscribe;
import org.hcz.mapper.user.UserSubscribeMapper;
import org.hcz.service.user.UserSubscribeService;
import org.springframework.stereotype.Service;



@Service
public class UserSubscribeServiceImpl  extends ServiceImpl<UserSubscribeMapper, UserSubscribe> implements UserSubscribeService {
}
