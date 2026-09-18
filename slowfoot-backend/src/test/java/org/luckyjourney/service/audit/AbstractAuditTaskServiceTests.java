package org.hcz.service.audit;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.hcz.service.audit.AbstractAuditTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hcz.constant.AuditTaskStatus;
import org.hcz.entity.BaseEntity;
import org.hcz.entity.audit.AuditTaskEntity;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


// 用手工搭的子类驱动基类逻辑：baseMapper 是 protected 字段，子类内部赋值即可注入 mock。
// 退避序列依赖 System.currentTimeMillis() 且无注入时钟，因此只断言「排了重试」和「延迟随重试次数增长」，
// 不钉具体秒数。

@ExtendWith(MockitoExtension.class)
class AbstractAuditTaskServiceTests {

    @Mock
    private BaseMapper<TestTask> baseMapper;

    private TestTaskService service;

    @BeforeEach
    void setUp() {
        service = new TestTaskService();
        service.setBaseMapper(baseMapper);
    }

    @Test
    void claimReturnsTrueWhenTheConditionalUpdateMatched() {
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertTrue(service.claim(1L, new Date()));

        UpdateWrapper<TestTask> wrapper = captureWrapper();
        assertTrue(wrapper.getSqlSet().contains("status"));
        assertTrue(wrapper.getSqlSet().contains("locked_until"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(AuditTaskStatus.PROCESSING.code()));
    }

    @Test
    void claimReturnsFalseWhenAnotherInstanceWon() {
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertFalse(service.claim(1L, new Date()));
    }

    @Test
    void markSuccessIgnoresNullTaskId() {
        service.markSuccess(null);

        verifyNoInteractions(baseMapper);
    }

    @Test
    void markSuccessClearsTheLock() {
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.markSuccess(1L);

        UpdateWrapper<TestTask> wrapper = captureWrapper();
        assertTrue(wrapper.getSqlSet().contains("locked_until"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(AuditTaskStatus.SUCCESS.code()));
    }

    @Test
    void markFailureIgnoresTaskThatIsNotProcessing() {
        when(baseMapper.selectById(1L)).thenReturn(task(AuditTaskStatus.SUCCESS.code(), 3));

        service.markFailure(1L, "boom");

        verify(baseMapper, never()).update(any(), any());
        assertFalse(service.businessFailed);
    }

    @Test
    void markFailureSchedulesRetryBelowTheLimit() {
        when(baseMapper.selectById(1L)).thenReturn(task(AuditTaskStatus.PROCESSING.code(), 2));
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.markFailure(1L, "provider down");

        UpdateWrapper<TestTask> wrapper = captureWrapper();
        assertTrue(wrapper.getSqlSet().contains("next_retry_at"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(AuditTaskStatus.PENDING.code()));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("provider down"));
        assertFalse(service.businessFailed, "未达上限不该推给人工");
    }

    @Test
    void markFailureBacksOffFurtherOnLaterRetries() {
        when(baseMapper.selectById(1L)).thenReturn(task(AuditTaskStatus.PROCESSING.code(), 1));
        when(baseMapper.update(any(), any())).thenReturn(1);
        service.markFailure(1L, "x");
        Date firstRetryAt = firstNextRetryAt();

        service = new TestTaskService();
        service.setBaseMapper(baseMapper);
        when(baseMapper.selectById(1L)).thenReturn(task(AuditTaskStatus.PROCESSING.code(), 3));
        service.markFailure(1L, "x");
        Date laterRetryAt = firstNextRetryAt();

        assertTrue(laterRetryAt.after(firstRetryAt), "重试次数越高，下次重试时间应越晚");
    }

    @Test
    void markFailureHandsOverToManualAtTheRetryLimit() {
        when(baseMapper.selectById(1L)).thenReturn(task(AuditTaskStatus.PROCESSING.code(), 5));
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.markFailure(1L, "provider down");

        UpdateWrapper<TestTask> wrapper = captureWrapper();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(AuditTaskStatus.FAILED.code()));
        assertTrue(service.businessFailed);
        assertEquals(1L, service.failedTask.getId());
    }

    @Test
    void markFailureUsesTheSubclassFallbackMessageWhenNull() {
        when(baseMapper.selectById(1L)).thenReturn(task(AuditTaskStatus.PROCESSING.code(), 1));
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.markFailure(1L, null);

        assertTrue(captureWrapper().getParamNameValuePairs().containsValue("兜底文案"));
    }

    @Test
    void markFailureTruncatesAnOverlongMessage() {
        when(baseMapper.selectById(1L)).thenReturn(task(AuditTaskStatus.PROCESSING.code(), 1));
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.markFailure(1L, "违".repeat(600));

        Object lastError = captureWrapper().getParamNameValuePairs().values().stream()
                .filter(String.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertEquals(500, lastError.toString().length());
    }

    @SuppressWarnings("unchecked")
    private UpdateWrapper<TestTask> captureWrapper() {
        ArgumentCaptor<UpdateWrapper<TestTask>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(baseMapper, org.mockito.Mockito.atLeastOnce()).update(any(), captor.capture());
        return captor.getValue();
    }

    private Date firstNextRetryAt() {
        return (Date) captureWrapper().getParamNameValuePairs().values().stream()
                .filter(Date.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private TestTask task(int status, int retryCount) {
        TestTask task = new TestTask();
        task.setId(1L);
        task.setStatus(status);
        task.setRetryCount(retryCount);
        return task;
    }

    static class TestTask extends BaseEntity implements AuditTaskEntity {

        private Integer status;
        private Integer retryCount;

        @Override
        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        @Override
        public Integer getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(Integer retryCount) {
            this.retryCount = retryCount;
        }
    }

    static class TestTaskService extends AbstractAuditTaskService<BaseMapper<TestTask>, TestTask> {

        boolean businessFailed;
        TestTask failedTask;

        @Override
        protected void markBusinessFailed(TestTask task) {
            this.businessFailed = true;
            this.failedTask = task;
        }

        @Override
        protected String fallbackErrorMessage() {
            return "兜底文案";
        }

        void setBaseMapper(BaseMapper<TestTask> mapper) {
            this.baseMapper = mapper;
        }
    }
}
