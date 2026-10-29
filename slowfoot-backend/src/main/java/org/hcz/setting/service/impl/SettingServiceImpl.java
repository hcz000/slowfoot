package org.hcz.setting.service.impl;

import org.hcz.setting.entity.Setting;
import org.hcz.setting.mapper.SettingMapper;
import org.hcz.setting.service.SettingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;



@Service
public class SettingServiceImpl extends ServiceImpl<SettingMapper, Setting> implements SettingService {

}
