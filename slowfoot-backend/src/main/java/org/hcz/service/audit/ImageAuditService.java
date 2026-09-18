package org.hcz.service.audit;

import com.fasterxml.jackson.databind.JsonNode;
import org.hcz.constant.AuditStatus;
import org.hcz.entity.response.AuditResponse;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.service.file.AliyunOssFileService;
import org.springframework.stereotype.Service;


// 使用阿里云内容安全进行图片审核。

@Service
public class ImageAuditService extends AbstractAuditService<String, AuditResponse> {

    private final AliyunContentSecurityClient contentSecurityClient;
    private final AliyunOssFileService ossFileService;

    public ImageAuditService(AliyunContentSecurityClient contentSecurityClient,
                             AliyunOssFileService ossFileService) {
        this.contentSecurityClient = contentSecurityClient;
        this.ossFileService = ossFileService;
    }

    @Override
    public AuditResponse audit(String urlOrObjectKey) {
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
