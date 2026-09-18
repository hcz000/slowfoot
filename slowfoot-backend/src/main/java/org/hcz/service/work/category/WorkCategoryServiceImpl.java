package org.hcz.service.work.category;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.hcz.entity.work.WorkCategory;
import org.hcz.mapper.work.WorkCategoryMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class WorkCategoryServiceImpl extends ServiceImpl<WorkCategoryMapper, WorkCategory> implements WorkCategoryService {


    @Override
    public List<String> getLabels(Long typeId) {
        final List<String> labels = this.getById(typeId).buildLabel();
        return labels;
    }

    @Override
    public List<String> random10Labels() {
        final List<WorkCategory> types = list(Wrappers.emptyWrapper());
        Collections.shuffle(types);
        final ArrayList<String> labels = new ArrayList<>();
        for (WorkCategory type : types) {
            for (String label : type.buildLabel()) {
                if (labels.size() == 10){
                    return labels;
                }
                labels.add(label);
            }
        }
        return labels;
    }
}
