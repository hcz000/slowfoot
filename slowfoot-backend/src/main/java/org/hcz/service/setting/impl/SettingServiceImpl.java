package org.hcz.service.setting.impl;

import org.hcz.entity.Setting;
import org.hcz.mapper.setting.SettingMapper;
import org.hcz.service.setting.SettingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;



@Service
public class SettingServiceImpl extends ServiceImpl<SettingMapper, Setting> implements SettingService {

}
