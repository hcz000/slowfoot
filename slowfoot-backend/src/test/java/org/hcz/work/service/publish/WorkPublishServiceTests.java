package org.hcz.work.service.publish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hcz.work.dto.TextWorkPublishRequest;
import org.hcz.work.entity.WorkCategory;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.work.mapper.ImageWorkMapper;
import org.hcz.work.mapper.TextWorkMapper;
import org.hcz.work.mapper.VideoWorkMapper;
import org.hcz.work.mapper.WorkAssetMapper;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.audit.service.WorkAuditTaskService;
import org.hcz.file.service.AliyunOssFileService;
import org.hcz.file.service.FileService;
import org.hcz.work.service.adapter.WorkContentAdapterRegistry;
import org.hcz.work.service.category.WorkCategoryService;
import org.hcz.work.service.publish.WorkPublishService;
import org.hcz.work.service.query.WorkViewSupport;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;


// 直测 WorkPublishService 的方法级行为。
// 不能手工 new 出来驱动发布落库分支——baseMapper 是 MyBatis-Plus 注入的字段而非构造参数；
// 但 publishText 的入参守卫在触达 baseMapper 之前就返回，因此可以直接单测。
// 审核相关逻辑在 WorkPublishAuditService，测试见 WorkPublishAuditServiceTests。

@ExtendWith(MockitoExtension.class)
class WorkPublishServiceTests {

    @Mock private WorkMapper workMapper;
    @Mock private WorkAssetMapper workAssetMapper;
    @Mock private ImageWorkMapper imageWorkMapper;
    @Mock private TextWorkMapper textWorkMapper;
    @Mock private VideoWorkMapper videoWorkMapper;
    @Mock private WorkCategoryService workCategoryService;
    @Mock private FileService fileService;
    @Mock private AliyunOssFileService ossFileService;
    @Mock private WorkContentAdapterRegistry adapterRegistry;
    @Mock private WorkAuditTaskService workAuditTaskService;
    @Mock private WorkViewSupport workViewSupport;

    private WorkPublishService workService;

    @BeforeEach
    void setUp() {
        workService = new WorkPublishService(workMapper, workAssetMapper, imageWorkMapper, textWorkMapper,
                videoWorkMapper, workCategoryService, fileService, ossFileService, adapterRegistry,
                workAuditTaskService, workViewSupport);
    }


// 全角空格能通过 @NotBlank（trim 语义），却会让 WorkDraft.text 抛异常变成 500。

    @Test
    void publishTextRejectsWhitespaceOnlyContent() {
        when(workCategoryService.getById(1L)).thenReturn(new WorkCategory());
        String ideographicSpace = "　";

        AppException exception = assertThrows(AppException.class,
                () -> workService.publishText(textRequest(ideographicSpace), 1L));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("正文不能为空", exception.getMessage());
    }

    private TextWorkPublishRequest textRequest(String content) {
        return new TextWorkPublishRequest("标题", null, content, 1L, null, Boolean.TRUE);
    }
}
