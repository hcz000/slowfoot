package org.hcz.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import org.hcz.config.LocalCache;
import org.hcz.service.file.AliyunOssFileService;
import org.hcz.entity.File;
import org.hcz.entity.Setting;
import org.hcz.util.UserHolder;
import org.hcz.service.file.FileService;
import org.hcz.service.setting.SettingService;
import org.hcz.util.ApiResponse;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;



@RestController
@RequestMapping("/luckyjourney/file")
public class FileController implements InitializingBean {


    @Autowired
    FileService fileService;

    @Autowired
    AliyunOssFileService ossFileService;

    @Autowired
    SettingService settingService;


// 保存到文件表
// @return

    @PostMapping
    public ApiResponse save(String fileKey){

        return ApiResponse.ok().data(fileService.save(fileKey, UserHolder.get()));
    }

    @GetMapping("/getToken")
    public ApiResponse token(@RequestParam(required = false) String type,
                             @RequestParam(required = false) String filename){

        return ApiResponse.ok().data(ossFileService.createUploadTicket(type, filename));
    }

    @GetMapping("/{fileId}")
    public void getUUid(HttpServletRequest request, HttpServletResponse response, @PathVariable  Long fileId) throws IOException {

     //   String ip = request.getHeader("referer");
     //   if (!LocalCache.containsKey(ip)) {
     //       response.sendError(HttpServletResponse.SC_FORBIDDEN);
     //       return;
     //   }
        // 如果不是指定ip调用的该接口，则不返回
        File url = fileService.getFileTrustUrl(fileId, UserHolder.get());
        response.setContentType(url.getType());
        response.sendRedirect(url.getFileKey());
    }

    @PostMapping("/auth")
    public void auth(@RequestParam(required = false) String uuid, HttpServletResponse response) throws IOException {
        if (uuid == null || LocalCache.containsKey(uuid) == null){
            response.sendError(401);
        }else {
            LocalCache.rem(uuid);
            response.sendError(200);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final Setting setting = settingService.list(Wrappers.emptyWrapper()).get(0);
        for (String s : setting.getAllowIp().split(",")) {
            LocalCache.put(s,true);
        }
    }
}
