package org.hcz.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hcz.admin.security.Auth;
import org.hcz.common.config.LocalCache;
import org.hcz.setting.entity.Setting;
import org.hcz.common.json.SettingScoreJson;
import org.hcz.setting.service.SettingService;
import org.hcz.common.util.ApiResponse;
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
