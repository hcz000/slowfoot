package org.hcz.service.audit;

import com.fasterxml.jackson.databind.JsonNode;
import org.hcz.constant.AuditStatus;
import org.hcz.entity.response.AuditResponse;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.springframework.stereotype.Service;


// 使用阿里云内容安全进行文本审核。

@Service
public class TextAuditService extends AbstractAuditService<String, AuditResponse> {

    private final AliyunContentSecurityClient contentSecurityClient;

    public TextAuditService(AliyunContentSecurityClient contentSecurityClient) {
        this.contentSecurityClient = contentSecurityClient;
    }

    @Override
    public AuditResponse audit(String text) {
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

    private AuditResponse passed() {
        return new AuditResponse(AuditStatus.SUCCESS.code(), "正常");
    }
}
