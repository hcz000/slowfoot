package org.hcz.work.service.category;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.hcz.work.entity.WorkCategory;
import org.hcz.work.mapper.WorkCategoryMapper;
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
