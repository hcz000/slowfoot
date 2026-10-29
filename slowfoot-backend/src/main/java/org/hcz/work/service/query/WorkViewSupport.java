package org.hcz.work.service.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.file.entity.File;
import org.hcz.user.entity.User;
import org.hcz.user.dto.UserVO;
import org.hcz.work.dto.WorkVO;
import org.hcz.work.entity.TextWork;
import org.hcz.work.entity.VideoWork;
import org.hcz.work.entity.Work;
import org.hcz.work.entity.WorkAssetEntity;
import org.hcz.work.entity.WorkType;
import org.hcz.work.mapper.ImageWorkMapper;
import org.hcz.work.mapper.TextWorkMapper;
import org.hcz.work.mapper.VideoWorkMapper;
import org.hcz.work.mapper.WorkAssetMapper;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.file.service.AliyunOssFileService;
import org.hcz.file.service.FileService;
import org.hcz.user.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


// 作品查询和对外载荷装配的共享逻辑。

@Component
public class WorkViewSupport {

    private final WorkMapper workMapper;
    private final WorkAssetMapper workAssetMapper;
    private final ImageWorkMapper imageWorkMapper;
    private final TextWorkMapper textWorkMapper;
    private final VideoWorkMapper videoWorkMapper;
    private final FileService fileService;
    private final AliyunOssFileService ossFileService;
    private final UserService userService;

    public WorkViewSupport(WorkMapper workMapper,
                           WorkAssetMapper workAssetMapper,
                           ImageWorkMapper imageWorkMapper,
                           TextWorkMapper textWorkMapper,
                           VideoWorkMapper videoWorkMapper,
                           FileService fileService,
                           AliyunOssFileService ossFileService,
                           UserService userService) {
        this.workMapper = workMapper;
        this.workAssetMapper = workAssetMapper;
        this.imageWorkMapper = imageWorkMapper;
        this.textWorkMapper = textWorkMapper;
        this.videoWorkMapper = videoWorkMapper;
        this.fileService = fileService;
        this.ossFileService = ossFileService;
        this.userService = userService;
    }

    public LambdaQueryWrapper<Work> videoOnly() {
        return new LambdaQueryWrapper<Work>().eq(Work::getWorkType, WorkType.VIDEO.name());
    }

    public Work getWorkRecord(Long id) {
        return workMapper.selectById(id);
    }

    public Collection<Work> listWorksByIds(Collection<Long> ids) {
        if (ObjectUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        final List<Work> works = workMapper.selectList(new LambdaQueryWrapper<Work>().in(Work::getId, ids));
        final Map<Long, Work> worksById = works.stream()
                .collect(Collectors.toMap(Work::getId, Function.identity(), (first, ignored) -> first,
                        LinkedHashMap::new));
        final List<Work> orderedWorks = new ArrayList<>(works.size());
        for (Long id : ids) {
            final Work work = worksById.get(id);
            if (work != null) {
                orderedWorks.add(work);
            }
        }
        return orderedWorks;
    }

    public Collection<WorkVO> toWorkVos(Collection<Work> works) {
        if (ObjectUtils.isEmpty(works)) {
            return Collections.emptyList();
        }
        works.forEach(this::loadDetails);
        final List<WorkVO> workVos = works.stream().map(WorkVO::of).collect(Collectors.toList());
        populateUserAndUrl(workVos);
        return workVos;
    }


// 展示字段装配：作者信息与主文件格式，全部集中在这里。

    public void populateUserAndUrl(Collection<WorkVO> works) {
        if (!ObjectUtils.isEmpty(works)) {
            Set<Long> userIds = new HashSet<>();
            final ArrayList<Long> fileIds = new ArrayList<>();
            for (WorkVO work : works) {
                userIds.add(work.getUserId());
                fileIds.add(work.getPrimaryFileId());
                fileIds.add(work.getCoverFileId());
            }
            final Map<Long, File> fileMap = fileService.listByIds(fileIds).stream()
                    .collect(Collectors.toMap(File::getId, Function.identity()));
            final Map<Long, User> userMap = userService.list(userIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            for (WorkVO work : works) {
                final UserVO userVO = new UserVO();
                final User user = userMap.get(work.getUserId());
                userVO.setId(work.getUserId());
                if (user != null) {
                    userVO.setNickName(user.getNickName());
                    userVO.setDescription(user.getDescription());
                    userVO.setSex(user.getSex());
                }
                work.setUser(userVO);
                final File file = fileMap.get(work.getPrimaryFileId());
                work.setFormat(file == null ? null : file.getFormat());
            }
        }
    }

    // 加载作品详情
    public void loadDetails(Work work) {
        work.setAssets(loadAssets(work.getId()));
        if (WorkType.TEXT.name().equals(work.getWorkType())) {
            TextWork textWork = textWorkMapper.selectById(work.getId());
            if (textWork != null) {
                work.setContent(textWork.getContent());
            }
        } else if (WorkType.VIDEO.name().equals(work.getWorkType())) {
            VideoWork videoWork = videoWorkMapper.selectById(work.getId());
            if (videoWork != null) {
                work.setPrimaryFileId(videoWork.getPrimaryFileId());
                work.setCoverFileId(videoWork.getCoverFileId());
                work.setDuration(videoWork.getDuration());
            }
        }
    }

    // 加载作品资源
    private List<WorkAssetEntity> loadAssets(Long workId) {
        List<WorkAssetEntity> assets = workAssetMapper.selectList(new LambdaQueryWrapper<WorkAssetEntity>()
                .eq(WorkAssetEntity::getWorkId, workId)
                .orderByAsc(WorkAssetEntity::getSortOrder)
                .orderByAsc(WorkAssetEntity::getId));
        for (WorkAssetEntity asset : assets) {
            File file = fileService.getById(asset.getFileId());
            if (file != null) {
                asset.setUrl(ossFileService.getAccessUrl(file.getFileKey()));
            }
        }
        return assets;
    }
}
