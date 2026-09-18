package org.hcz.service.work.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.entity.work.WorkShare;
import org.hcz.mapper.work.WorkShareMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;



@Service
public class WorkShareServiceImpl extends ServiceImpl<WorkShareMapper, WorkShare> implements WorkShareService {

    @Override
    public boolean share(WorkShare workShare) {

        try {
            // 利用workId和ip做为唯一索引,少一次查询
            this.save(workShare);
        }catch (Exception e){
            // 不用删除
            return false;
        }
        return true;
    }

    @Override
    public List<Long> getShareUserIds(Long workId) {
        return this.list(new LambdaQueryWrapper<WorkShare>().eq(WorkShare::getWorkId, workId)).stream().map(WorkShare::getUserId).collect(Collectors.toList());
    }


}
