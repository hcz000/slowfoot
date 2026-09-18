package org.hcz.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.hcz.authority.Auth;
import org.hcz.entity.work.WorkCategory;
import org.hcz.entity.vo.BasePage;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.service.work.category.WorkCategoryService;
import org.hcz.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/admin/type")
public class AdminTypeController {

    @Autowired
    private WorkCategoryService workCategoryService;


    @GetMapping("/{id}")
    @Auth("admin:type:get")
    public ApiResponse get(@PathVariable Long id){
        return ApiResponse.ok().data(workCategoryService.getById(id));
    }


    @GetMapping("/page")
    @Auth("admin:type:page")
    public ApiResponse page(BasePage basePage){
        final IPage<WorkCategory> page = workCategoryService.page(basePage.page(), null);
        return ApiResponse.ok().data(page.getRecords()).page(page.getCurrent(), page.getSize(), page.getTotal());
    }

    @PostMapping
    @Auth("admin:type:add")
    public ApiResponse add(@RequestBody @Validated WorkCategory type){
        final long count = workCategoryService.count(new LambdaQueryWrapper<WorkCategory>().eq(WorkCategory::getName, type.getName()).ne(WorkCategory::getId,type.getId()));
        if (count == 1) throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "分类已存在");
        workCategoryService.save(type);
        return ApiResponse.ok().message("添加成功");
    }

    @PutMapping
    @Auth("admin:type:update")
    public ApiResponse update(@RequestBody @Validated WorkCategory type){
        final long count =  workCategoryService.count(new LambdaQueryWrapper<WorkCategory>().eq(WorkCategory::getName, type.getName()).ne(WorkCategory::getId,type.getId()));
        if (count == 1) throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "分类已存在");
        workCategoryService.updateById(type);
        return ApiResponse.ok().message("修改成功");
    }

    @DeleteMapping("/{id}")
    @Auth("admin:type:delete")
    public ApiResponse delete(@PathVariable Long id){
        workCategoryService.removeById(id);
        return ApiResponse.ok().message("删除成功");
    }

}
