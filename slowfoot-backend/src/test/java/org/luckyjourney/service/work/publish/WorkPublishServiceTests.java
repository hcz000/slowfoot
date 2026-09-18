package org.luckyjourney.service.work.publish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hcz.entity.vo.TextWorkPublishRequest;
import org.hcz.entity.work.WorkCategory;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.mapper.work.ImageWorkMapper;
import org.hcz.mapper.work.TextWorkMapper;
import org.hcz.mapper.work.VideoWorkMapper;
import org.hcz.mapper.work.WorkAssetMapper;
import org.hcz.mapper.work.WorkMapper;
import org.hcz.service.audit.WorkAuditTaskService;
import org.hcz.service.file.AliyunOssFileService;
import org.hcz.service.file.FileService;
import org.hcz.service.work.adapter.WorkContentAdapterRegistry;
import org.hcz.service.work.category.WorkCategoryService;
import org.hcz.service.work.publish.WorkPublishService;
import org.hcz.service.work.query.WorkViewSupport;
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
