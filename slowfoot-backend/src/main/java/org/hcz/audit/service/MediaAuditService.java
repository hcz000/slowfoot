package org.hcz.audit.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import org.hcz.audit.client.AliyunContentSecurityClient;
import org.hcz.audit.vo.AuditResponse;
import org.hcz.common.constant.AuditMsgMap;
import org.hcz.common.constant.AuditStatus;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.file.service.AliyunOssFileService;
import org.hcz.setting.entity.Setting;
import org.hcz.setting.service.SettingService;
import org.springframework.stereotype.Service;


// 媒体审核统一入口：文本 / 图片是一次往返即终局的同步调用；
// 视频是异步任务制，拆为 submit（提交拿 taskId）与 fetch（单次查询结论），
// 等待结论的过程由 video_audit_job 状态机与轮询调度器承载，本类不 sleep。

@Service
public class MediaAuditService {

    public enum VideoState {
        FINISHED,
        PENDING,
        FAILED
    }

    // FINISHED 时 response 必不为空；其余状态 response 为 null。
    public record VideoOutcome(VideoState state, AuditResponse response) {
    }

    private final AliyunContentSecurityClient contentSecurityClient;
    private final AliyunOssFileService ossFileService;
    private final SettingService settingService;

    public MediaAuditService(AliyunContentSecurityClient contentSecurityClient,
                             AliyunOssFileService ossFileService,
                             SettingService settingService) {
        this.contentSecurityClient = contentSecurityClient;
        this.ossFileService = ossFileService;
        this.settingService = settingService;
    }

    public AuditResponse auditText(String text) {
        if (!isNeedAudit()) {
            return passed();
        }
        try {
            final JsonNode response = contentSecurityClient.scanText(text);
            return mapAliyunResult(response);
        } catch (Exception e) {
            throw new AppException(ErrorCode.DEPENDENCY_FAILURE, "文本审核服务暂时不可用", e);
        }
    }

    public AuditResponse auditImage(String urlOrObjectKey) {
        if (!isNeedAudit()) {
            return passed();
        }
        try {
            final String url = toAccessUrl(urlOrObjectKey);
            final JsonNode response = contentSecurityClient.scanImage(url);
            return mapAliyunResult(response);
        } catch (Exception e) {
            throw new AppException(ErrorCode.DEPENDENCY_FAILURE, "图片审核服务暂时不可用", e);
        }
    }


// 只提交、不等待。返回阿里云 taskId；审核开关关闭时返回 null 表示无需异步审核。
// 提交失败抛 DEPENDENCY_FAILURE，由调用方的任务重试机制接管。

    public String submitVideo(String url) {
        if (!isNeedAudit()) {
            return null;
        }
        try {
            final JsonNode submitResponse = contentSecurityClient.submitVideo(url);
            final String taskId = firstAliyunResult(submitResponse).path("taskId").asText("");
            if (taskId.isBlank()) {
                throw new IllegalStateException("阿里云视频审核未返回任务 ID");
            }
            return taskId;
        } catch (Exception e) {
            throw new AppException(ErrorCode.DEPENDENCY_FAILURE, "视频审核服务暂时不可用", e);
        }
    }


// 单次查询视频审核结论，不阻塞等待。

    public VideoOutcome fetchVideo(String aliyunTaskId) {
        try {
            final JsonNode resultResponse = contentSecurityClient.queryVideo(aliyunTaskId);
            final JsonNode result = firstAliyunResult(resultResponse);
            final String status = result.path("status").asText("");
            if (hasConclusion(result) || "FINISHED".equalsIgnoreCase(status)
                    || "SUCCESS".equalsIgnoreCase(status)) {
                return new VideoOutcome(VideoState.FINISHED, mapAliyunResult(resultResponse));
            }
            if ("FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
                return new VideoOutcome(VideoState.FAILED, null);
            }
            return new VideoOutcome(VideoState.PENDING, null);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.DEPENDENCY_FAILURE, "视频审核服务暂时不可用", e);
        }
    }

    private boolean isNeedAudit() {
        final Setting setting = settingService.list(Wrappers.emptyWrapper()).stream().findFirst().orElse(null);
        return setting != null && Boolean.TRUE.equals(setting.getAuditOpen());
    }

    private AuditResponse mapAliyunResult(JsonNode response) {
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

    private JsonNode firstAliyunResult(JsonNode response) {
        JsonNode result = response.path("data");
        if (result.isArray() && !result.isEmpty()) {
            return result.get(0);
        }
        return result;
    }

    private boolean hasConclusion(JsonNode result) {
        if (result.hasNonNull("suggestion")) {
            return true;
        }
        final JsonNode results = result.path("results");
        return results.isArray() && !results.isEmpty()
                && results.get(0).hasNonNull("suggestion");
    }

    private String toAccessUrl(String urlOrObjectKey) {
        if (urlOrObjectKey != null
                && (urlOrObjectKey.startsWith("http://") || urlOrObjectKey.startsWith("https://"))) {
            return urlOrObjectKey;
        }
        return ossFileService.getAccessUrl(urlOrObjectKey);
    }

    private AuditResponse passed() {
        return new AuditResponse(AuditStatus.SUCCESS.code(), "正常");
    }
}
