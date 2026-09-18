package org.hcz.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hcz.authority.Auth;
import org.hcz.config.LocalCache;
import org.hcz.entity.Setting;
import org.hcz.entity.json.SettingScoreJson;
import org.hcz.service.setting.SettingService;
import org.hcz.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/admin/setting")
public class AdminSettingController {

    @Autowired
    private SettingService settingService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    @Auth("admin:setting:get")
    public ApiResponse get() throws JsonProcessingException {
        final Setting setting = settingService.list(Wrappers.emptyWrapper()).get(0);
        final SettingScoreJson settingScoreJson = objectMapper.readValue(setting.getAuditPolicy(), SettingScoreJson.class);
        setting.setSettingScoreJson(settingScoreJson);
        return ApiResponse.ok().data(setting);
    }


    @PutMapping
    @Auth("admin:setting:update")
    public ApiResponse update(@RequestBody @Validated Setting setting){
        settingService.updateById(setting);
        for (String s : setting.getAllowIp().split(",")) {
            LocalCache.put(s,true);
        }
        return ApiResponse.ok().message("修改成功");
    }
}
