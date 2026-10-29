package org.hcz.work.service.publish;

import org.hcz.common.constant.AuditStatus;
import org.hcz.common.constant.AuditTaskStatus;
import org.hcz.audit.entity.WorkAuditTask;
import org.hcz.work.dto.ImageWorkPublishRequest;
import org.hcz.work.dto.TextWorkPublishRequest;
import org.hcz.work.dto.VideoWorkPublishRequest;
import org.hcz.work.entity.ImageWork;
import org.hcz.work.entity.TextWork;
import org.hcz.work.entity.VideoWork;
import org.hcz.work.entity.Work;
import org.hcz.work.entity.WorkAsset;
import org.hcz.work.entity.WorkAssetEntity;
import org.hcz.work.entity.WorkDraft;
import org.hcz.work.entity.WorkType;
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
import org.hcz.work.service.query.WorkViewSupport;
import org.hcz.common.util.FileUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class WorkPublishService {

    private final WorkMapper workMapper;
    private final WorkAssetMapper workAssetMapper;
    private final ImageWorkMapper imageWorkMapper;
    private final TextWorkMapper textWorkMapper;
    private final VideoWorkMapper videoWorkMapper;
    private final WorkCategoryService workCategoryService;
    private final FileService fileService;
    private final AliyunOssFileService ossFileService;
    private final WorkContentAdapterRegistry adapterRegistry;
    private final WorkAuditTaskService workAuditTaskService;
    private final WorkViewSupport viewSupport;

    public WorkPublishService(WorkMapper workMapper,
                                  WorkAssetMapper workAssetMapper,
                                  ImageWorkMapper imageWorkMapper,
                                  TextWorkMapper textWorkMapper,
                                  VideoWorkMapper videoWorkMapper,
                                  WorkCategoryService workCategoryService,
                                  FileService fileService,
                                  AliyunOssFileService ossFileService,
                                  WorkContentAdapterRegistry adapterRegistry,
                                  WorkAuditTaskService workAuditTaskService,
                                  WorkViewSupport viewSupport) {
        this.workMapper = workMapper;
        this.workAssetMapper = workAssetMapper;
        this.imageWorkMapper = imageWorkMapper;
        this.textWorkMapper = textWorkMapper;
        this.videoWorkMapper = videoWorkMapper;
        this.workCategoryService = workCategoryService;
        this.fileService = fileService;
        this.ossFileService = ossFileService;
        this.adapterRegistry = adapterRegistry;
        this.workAuditTaskService = workAuditTaskService;
        this.viewSupport = viewSupport;
    }

    @Transactional
    public Work publishImage(ImageWorkPublishRequest request, Long userId) {
        requireUser(userId);
        requireCategory(request.typeId(), "作品分类不存在");

        final WorkDraft draft = WorkDraft.images(request.imageFileIds());
        adapterRegistry.get(WorkType.IMAGE).validate(draft, userId);

        Work work = new Work();
        work.setWorkNo("W" + UUID.randomUUID().toString().replace("-", ""));
        work.setUserId(userId);
        work.setWorkType(WorkType.IMAGE.name());
        work.setPrimaryFileId(draft.assets().get(0).fileId());
        work.setTitle(request.title().trim());
        work.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        work.setTypeId(request.typeId());
        work.setLabelNames(StringUtils.hasText(request.labelNames()) ? request.labelNames().trim() : null);
        work.setCoverFileId(draft.assets().get(0).fileId());
        initializeCounters(work, request.openState());
        workMapper.insert(work);

        ImageWork imageWork = new ImageWork();
        imageWork.setId(work.getId());
        imageWorkMapper.insert(imageWork);
        saveAssets(work.getId(), draft.assets());
        enqueueAudit(work);
        viewSupport.loadDetails(work);
        return work;
    }

    @Transactional
    public Work publishText(TextWorkPublishRequest request, Long userId) {
        requireUser(userId);
        requireCategory(request.typeId(), "work category does not exist");

        String content = request.content().trim();
        if (!StringUtils.hasText(content)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "正文不能为空");
        }
        WorkDraft draft = WorkDraft.text(content);
        adapterRegistry.get(WorkType.TEXT).validate(draft, userId);

        Work work = new Work();
        work.setWorkNo("W" + UUID.randomUUID().toString().replace("-", ""));
        work.setUserId(userId);
        work.setWorkType(WorkType.TEXT.name());
        work.setTitle(request.title().trim());
        work.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        work.setContent(content);
        work.setTypeId(request.typeId());
        work.setLabelNames(StringUtils.hasText(request.labelNames()) ? request.labelNames().trim() : null);
        initializeCounters(work, request.openState());
        workMapper.insert(work);

        TextWork textWork = new TextWork();
        textWork.setId(work.getId());
        textWork.setContent(content);
        textWorkMapper.insert(textWork);
        enqueueAudit(work);
        viewSupport.loadDetails(work);
        return work;
    }

    @Transactional
    public Work publishVideoWork(VideoWorkPublishRequest request, Long userId) {
        requireUser(userId);
        final Long workId = request.id();
        final boolean isAdd = workId == null;

        Work work = new Work();
        work.setId(workId);
        work.setTitle(request.title().trim());
        work.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        work.setTypeId(request.typeId());
        work.setLabelNames(StringUtils.hasText(request.labelNames()) ? request.labelNames().trim() : null);
        work.setPrimaryFileId(request.videoFileId());
        work.setCoverFileId(request.coverFileId());

        Work oldWork = null;
        if (!isAdd) {
            oldWork = workMapper.selectOne(viewSupport.videoOnly()
                    .eq(Work::getId, workId).eq(Work::getUserId, userId));
            if (oldWork == null) throw new AppException(ErrorCode.WORK_NOT_FOUND);
            if (!Objects.equals(work.buildPrimaryFileUrl(), oldWork.buildPrimaryFileUrl())
                    || !Objects.equals(work.buildCoverUrl(), oldWork.buildCoverUrl())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "不能更换视频源，只能修改视频信息");
            }
        }

        requireCategory(request.typeId(), "分类不存在");
        if (work.buildLabel().size() > 5) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "标签最多只能选择5个");
        }
        work.setAuditStatus(AuditStatus.PROCESS.code());
        work.setUserId(userId);
        work.setWorkType(WorkType.VIDEO.name());
        work.setOpenState(request.openState());

        if (isAdd) {
            if (work.getPrimaryFileId() == null || work.getPrimaryFileId() <= 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "视频源不能为空");
            }
            adapterRegistry.get(WorkType.VIDEO)
                    .validate(WorkDraft.video(work.getPrimaryFileId(), work.getCoverFileId()), userId);
            if (ObjectUtils.isEmpty(work.getCoverFileId())) {
                work.setCoverFileId(fileService.generatePhoto(work.getPrimaryFileId(), userId));
            }
            work.setWorkNo("W" + UUID.randomUUID().toString().replace("-", "").substring(8));
        } else {
            work.setPrimaryFileId(null);
            work.setCoverFileId(null);
            work.setLabelNames(null);
        }

        if (isAdd || !StringUtils.hasLength(oldWork == null ? null : oldWork.getDuration())) {
            Long fileId = work.getPrimaryFileId();
            if (fileId == null || fileId == 0) {
                if (oldWork == null || oldWork.getPrimaryFileId() == null) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "视频源不能为空");
                }
                fileId = oldWork.getPrimaryFileId();
            }
            final String fileKey = fileService.getById(fileId).getFileKey();
            work.setDuration(FileUtil.getVideoDuration(ossFileService.getAccessUrl(fileKey)));
        }

        if (isAdd) {
            workMapper.insert(work);
        } else {
            workMapper.updateById(work);
        }
        VideoWork videoWork = videoWorkMapper.selectById(work.getId());
        if (videoWork == null) {
            videoWork = new VideoWork();
            videoWork.setId(work.getId());
        }
        videoWork.setPrimaryFileId(work.getPrimaryFileId() == null && oldWork != null
                ? oldWork.getPrimaryFileId() : work.getPrimaryFileId());
        videoWork.setCoverFileId(work.getCoverFileId() == null && oldWork != null
                ? oldWork.getCoverFileId() : work.getCoverFileId());
        videoWork.setDuration(work.getDuration() == null && oldWork != null
                ? oldWork.getDuration() : work.getDuration());
        videoWorkMapper.insertOrUpdate(videoWork);
        enqueueAudit(work, isAdd, oldWork);
        return work;
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    private void requireCategory(Long typeId, String message) {
        if (workCategoryService.getById(typeId) == null) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, message);
        }
    }

    private void initializeCounters(Work work, Boolean openState) {
        work.setOpenState(Boolean.TRUE.equals(openState));
        work.setAuditStatus(AuditStatus.PROCESS.code());
        work.setLikeCount(0L);
        work.setShareCount(0L);
        work.setViewCount(0L);
        work.setFavoriteCount(0L);
    }

    private void enqueueAudit(Work work) {
        enqueueAudit(work, null, null, null, null, null);
    }

    private void enqueueAudit(Work work, boolean isAdd, Work oldWork) {
        enqueueAudit(work, isAdd,
                oldWork == null ? null : oldWork.getOpenState(),
                Boolean.TRUE,
                oldWork == null ? null : oldWork.getTitle(),
                oldWork == null ? null : oldWork.getDescription());
    }

    private void enqueueAudit(Work work, Boolean isAdd, Boolean oldState, Boolean newState,
                              String oldTitle, String oldDescription) {
        WorkAuditTask task = new WorkAuditTask();
        task.setEventId(UUID.randomUUID().toString());
        task.setWorkId(work.getId());
        task.setIsAdd(isAdd == null ? Boolean.TRUE : isAdd);
        task.setOldState(oldState);
        task.setNewState(newState);
        task.setOldTitle(oldTitle);
        task.setOldDescription(oldDescription);
        task.setStatus(AuditTaskStatus.PENDING.code());
        task.setRetryCount(0);
        task.setNextRetryAt(new Date());
        workAuditTaskService.save(task);
    }

    private List<WorkAssetEntity> saveAssets(Long workId, List<WorkAsset> draftAssets) {
        Date now = new Date();
        List<WorkAssetEntity> assets = new ArrayList<>();
        for (WorkAsset asset : draftAssets) {
            WorkAssetEntity entity = new WorkAssetEntity();
            entity.setWorkId(workId);
            entity.setFileId(asset.fileId());
            entity.setAssetType(asset.assetType().name());
            entity.setSortOrder(asset.sortOrder());
            entity.setGmtCreated(now);
            entity.setGmtUpdated(now);
            workAssetMapper.insert(entity);
            assets.add(entity);
        }
        return assets;
    }
}
