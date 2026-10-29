package org.hcz.common.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonAlias;

import org.hcz.user.controller.CustomerController;



@Data
public class Model {
    private String label;
    @JsonAlias("videoId")
    private Long workId;

// 暴露的接口只根据停留时长 {@link CustomerController#updateUserModel}


    private Double score;


// 本次播放已观看的秒数；新观看上报只提交该字段，不信任客户端总时长。

    private Double watchTimeSeconds;


// 一次播放的客户端会话标识，用于防止重复上报重复累计。

    private String watchSessionId;
}
