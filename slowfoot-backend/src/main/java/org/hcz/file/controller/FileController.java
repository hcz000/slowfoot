package org.hcz.file.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import org.hcz.common.config.LocalCache;
import org.hcz.file.dto.MultipartCompleteRequest;
import org.hcz.file.service.AliyunOssFileService;
import org.hcz.file.entity.File;
import org.hcz.setting.entity.Setting;
import org.hcz.common.util.UserHolder;
import org.hcz.file.service.FileService;
import org.hcz.setting.service.SettingService;
import org.hcz.common.util.ApiResponse;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;



@RestController
@RequestMapping("/slowfoot/file")
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

    // --- 分片上传端点 ---

    @PostMapping("/multipart/initiate")
    public ApiResponse multipartInitiate(@RequestParam String type,
                                         @RequestParam String filename) {
        return ApiResponse.ok().data(ossFileService.initiateMultipartUpload(type, filename));
    }

    @GetMapping("/multipart/partUrl")
    public ApiResponse multipartPartUrl(@RequestParam String objectKey,
                                        @RequestParam String uploadId,
                                        @RequestParam int partNumber) {
        return ApiResponse.ok().data(ossFileService.generatePartUrl(objectKey, uploadId, partNumber));
    }

    @PostMapping("/multipart/complete")
    public ApiResponse multipartComplete(@RequestBody MultipartCompleteRequest request) {
        final String tempKey = ossFileService.completeMultipartUpload(request);
        // 注册到 file 表（内部会 copy 到正式路径）
        final Long fileId = fileService.save(tempKey, UserHolder.get());
        return ApiResponse.ok().data(fileId);
    }

    @PostMapping("/multipart/abort")
    public ApiResponse multipartAbort(@RequestParam String objectKey,
                                      @RequestParam String uploadId) {
        ossFileService.abortMultipartUpload(objectKey, uploadId);
        return ApiResponse.ok().message("分片上传已中止");
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
