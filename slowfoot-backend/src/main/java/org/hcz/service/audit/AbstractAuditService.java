package org.hcz.service.audit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import org.hcz.constant.AuditMsgMap;
import org.hcz.constant.AuditStatus;
import org.hcz.entity.Setting;
import org.hcz.entity.response.AuditResponse;
import org.hcz.service.setting.SettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


// 审核服务公共逻辑。

@Service
public abstract class AbstractAuditService<T, R> implements AuditService<T, R> {

    @Autowired
    protected SettingService settingService;


// 根据系统配置判断是否开启内容审核。

    protected boolean isNeedAudit() {
        final Setting setting = settingService.list(Wrappers.emptyWrapper()).stream().findFirst().orElse(null);
        return setting != null && Boolean.TRUE.equals(setting.getAuditOpen());
    }


// 将阿里云审核响应中的首个任务结果转换成项目统一的审核结果。

    protected AuditResponse mapAliyunResult(JsonNode response) {
        JsonNode result = response.path("data");
        if (result.isArray() && !result.isEmpty()) {
            result = result.get(0);
        }
        if (result.has("results") && result.path("results").isArray()
                && !result.path("results").isEmpty()) {
            result = result.path("results").get(0);
        }

        final String suggestion = result.path("suggestion").asText("");
        if (suggestion.isBlank()) {
            throw new IllegalStateException("阿里云内容安全未返回审核结论");
        }

        final AuditResponse auditResponse = new AuditResponse();
        auditResponse.setFlag(false);
        if ("pass".equalsIgnoreCase(suggestion)) {
            auditResponse.setAuditStatus(AuditStatus.SUCCESS.code());
            auditResponse.setMsg("正常");
            return auditResponse;
        }

        auditResponse.setAuditStatus(AuditStatus.PASS.code());
        final String label = result.path("label").asText("");
        final String message = AuditMsgMap.getInfo(label);
        auditResponse.setMsg(message == null || message.isBlank() ? "内容未通过审核" : message);
        return auditResponse;
    }


// 提取异步审核结果中的任务状态。

    protected JsonNode firstAliyunResult(JsonNode response) {
        JsonNode result = response.path("data");
        if (result.isArray() && !result.isEmpty()) {
            return result.get(0);
        }
        return result;
    }
}
