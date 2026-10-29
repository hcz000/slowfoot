package org.hcz.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.hcz.admin.security.Auth;
import org.hcz.common.constant.AuditStatus;
import org.hcz.common.vo.BasePage;
import org.hcz.audit.dto.WorkAuditRequest;
import org.hcz.audit.dto.WorkAuditVO;
import org.hcz.work.dto.WorkStatistics;
import org.hcz.work.entity.Work;
import org.hcz.work.entity.WorkType;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.work.service.WorkService;
import org.hcz.common.util.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;


// 管理端作品管理：审核队列、详情、删除、违规下架与统计，覆盖三种作品类型。

@RestController
@RequestMapping("/admin/work")
public class AdminWorkController {

    private final WorkService workService;

    public AdminWorkController(WorkService workService) {
        this.workService = workService;
    }


// 审核队列只包含审核中（1）和待人工复核（3），已通过或已驳回的作品不会返回。
// workType 建议一并传：idx_work_type_status 以 work_type 打头，只传 audit_status 用不上索引。

    @GetMapping("/page")
    @Auth("admin:work:page")
    public ApiResponse page(BasePage basePage,
                            @RequestParam(required = false) Integer auditStatus,
                            @RequestParam(required = false) String workType,
                            @RequestParam(required = false) String workNo,
                            @RequestParam(required = false) String title) {
        IPage<WorkAuditVO> page = workService.pageForAudit(basePage, auditStatus, workType, workNo, title);
        return ApiResponse.ok().data(page.getRecords())
                .page(page.getCurrent(), page.getSize(), page.getTotal());
    }


// 作品详情。管理端需要看到私密作品，因此不走面向用户的公开读取。

    @GetMapping("/{id}")
    @Auth("admin:work:get")
    public ApiResponse get(@PathVariable Long id) {
        return ApiResponse.ok().data(workService.getAuditDetail(id));
    }


// 人工复审：放行传 auditStatus=0，驳回传 2。

    @PostMapping("/audit")
    @Auth("admin:work:audit")
    public ApiResponse audit(@RequestBody @Validated WorkAuditRequest request) {
        workService.audit(request);
        return ApiResponse.ok().message("作品审核完成");
    }


// 删除作品。

    @DeleteMapping("/{id}")
    @Auth("admin:work:delete")
    public ApiResponse delete(@PathVariable Long id) {
        workService.deleteForAdmin(id);
        return ApiResponse.ok().message("删除成功");
    }


// 违规下架：转为私密并从推荐池、关注流中移除。

    @PostMapping("/violations/{id}")
    @Auth("admin:work:violations")
    public ApiResponse violations(@PathVariable Long id) {
        workService.violations(id);
        return ApiResponse.ok().message("下架成功");
    }


// 作品数据统计，覆盖全部作品类型。

    @GetMapping("/statistics")
    @Auth("admin:work:statistics")
    public ApiResponse statistics() {
        WorkStatistics statistics = new WorkStatistics();
        statistics.setAllCount(workService.count());
        statistics.setProcessCount(countByStatus(AuditStatus.PROCESS.code()));
        statistics.setSuccessCount(countByStatus(AuditStatus.SUCCESS.code()));
        statistics.setPassCount(countByStatus(AuditStatus.PASS.code()));

        Map<String, Long> byWorkType = new LinkedHashMap<>();
        for (WorkType type : WorkType.values()) {
            byWorkType.put(type.name(), workService.count(
                    new LambdaQueryWrapper<Work>().eq(Work::getWorkType, type.name())));
        }
        statistics.setByWorkType(byWorkType);
        return ApiResponse.ok().data(statistics);
    }

    private long countByStatus(Integer auditStatus) {
        return workService.count(new LambdaQueryWrapper<Work>().eq(Work::getAuditStatus, auditStatus));
    }
}
