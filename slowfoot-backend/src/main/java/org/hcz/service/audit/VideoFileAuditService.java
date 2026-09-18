package org.hcz.service.audit;

import com.fasterxml.jackson.databind.JsonNode;
import org.hcz.constant.AuditStatus;
import org.hcz.entity.response.AuditResponse;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.springframework.stereotype.Service;


// 使用阿里云内容安全对视频「文件」做异步审核。与作品审核流水线无关，只判断媒体本身是否合规。

@Service
public class VideoFileAuditService extends AbstractAuditService<String, AuditResponse> {

    private static final int MAX_POLL_ATTEMPTS = 60;
    private static final long POLL_INTERVAL_MILLIS = 2_000L;

    private final AliyunContentSecurityClient contentSecurityClient;

    public VideoFileAuditService(AliyunContentSecurityClient contentSecurityClient) {
        this.contentSecurityClient = contentSecurityClient;
    }

    @Override
    public AuditResponse audit(String url) {
        if (!isNeedAudit()) {
            return passed();
        }
        try {
            final JsonNode submitResponse = contentSecurityClient.submitVideo(url);
            final String taskId = firstAliyunResult(submitResponse).path("taskId").asText("");
            if (taskId.isBlank()) {
                throw new IllegalStateException("阿里云视频审核未返回任务 ID");
            }

            for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
                final JsonNode resultResponse = contentSecurityClient.queryVideo(taskId);
                final JsonNode result = firstAliyunResult(resultResponse);
                final String status = result.path("status").asText("");
                if (hasConclusion(result) || "FINISHED".equalsIgnoreCase(status)
                        || "SUCCESS".equalsIgnoreCase(status)) {
                    return mapAliyunResult(resultResponse);
                }
                if ("FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
                    throw new IllegalStateException("阿里云视频审核任务失败");
                }
                Thread.sleep(POLL_INTERVAL_MILLIS);
            }
            throw new IllegalStateException("阿里云视频审核超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.DEPENDENCY_FAILURE, "视频审核被中断", e);
        } catch (Exception e) {
            throw new AppException(ErrorCode.DEPENDENCY_FAILURE, "视频审核服务暂时不可用", e);
        }
    }

    private boolean hasConclusion(JsonNode result) {
        if (result.hasNonNull("suggestion")) {
            return true;
        }
        final JsonNode results = result.path("results");
        return results.isArray() && !results.isEmpty()
                && results.get(0).hasNonNull("suggestion");
    }

    private AuditResponse passed() {
        return new AuditResponse(AuditStatus.SUCCESS.code(), "正常");
    }
}
