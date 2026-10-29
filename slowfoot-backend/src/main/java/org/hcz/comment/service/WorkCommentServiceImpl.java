package org.hcz.comment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.common.constant.AuditStatus;
import org.hcz.common.constant.AuditTaskStatus;
import org.hcz.audit.entity.CommentAuditTask;
import org.hcz.user.entity.User;
import org.hcz.work.entity.Work;
import org.hcz.comment.entity.WorkComment;
import org.hcz.common.vo.BasePage;
import org.hcz.comment.dto.CommentAuditRequest;
import org.hcz.comment.dto.CommentCreateRequest;
import org.hcz.comment.dto.WorkCommentVO;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.comment.mapper.WorkCommentMapper;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.audit.service.CommentAuditService;
import org.hcz.audit.service.CommentAuditTaskService;
import org.hcz.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkCommentServiceImpl extends ServiceImpl<WorkCommentMapper, WorkComment>
        implements WorkCommentService {

    @Autowired
    private WorkMapper workMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private CommentAuditService commentAuditService;

    @Autowired
    private CommentAuditTaskService commentAuditTaskService;

    @Override
    @Transactional
    public WorkComment create(CommentCreateRequest request, Long userId) {
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "评论内容不能为空");
        }

        Work work = workMapper.selectById(request.getWorkId());
        if (work == null || !Objects.equals(work.getAuditStatus(), AuditStatus.SUCCESS.code())
                || Boolean.TRUE.equals(work.getOpenState())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "视频不存在或暂不可评论");
        }

        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        WorkComment comment = new WorkComment();
        comment.setWorkId(work.getId());
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setRootId(0L);
        comment.setContent(request.getContent().trim());
        comment.setAuditStatus(AuditStatus.PROCESS.code());
        comment.setAuditMsg("等待审核");
        comment.setNotificationSent(false);
        comment.setAuditNotificationSent(false);

        if (parentId != 0L) {
            WorkComment parent = getById(parentId);
            if (parent == null || !Objects.equals(parent.getWorkId(), work.getId())
                    || !Objects.equals(parent.getAuditStatus(), AuditStatus.SUCCESS.code())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "回复的评论不存在或暂不可回复");
            }
            comment.setRootId(parent.getRootId() == null || parent.getRootId() == 0L
                    ? parent.getId() : parent.getRootId());
            comment.setReplyToUserId(parent.getUserId());
        }

        save(comment);
        if (parentId == 0L) {
            UpdateWrapper<WorkComment> rootWrapper = new UpdateWrapper<>();
            rootWrapper.set("root_id", comment.getId()).eq("id", comment.getId());
            update(null, rootWrapper);
            comment.setRootId(comment.getId());
        }

        CommentAuditTask auditTask = new CommentAuditTask();
        auditTask.setEventId(UUID.randomUUID().toString());
        auditTask.setCommentId(comment.getId());
        auditTask.setStatus(AuditTaskStatus.PENDING.code());
        auditTask.setRetryCount(0);
        auditTask.setNextRetryAt(new Date());
        commentAuditTaskService.save(auditTask);
        return comment;
    }

    @Override
    public IPage<WorkCommentVO> pageApproved(Long workId, BasePage basePage) {
        Page<WorkComment> rootPage = new Page<>(basePage.getPage(), basePage.getLimit());
        LambdaQueryWrapper<WorkComment> rootWrapper = new LambdaQueryWrapper<WorkComment>()
                .eq(WorkComment::getWorkId, workId)
                .eq(WorkComment::getParentId, 0L)
                .eq(WorkComment::getAuditStatus, AuditStatus.SUCCESS.code())
                .orderByDesc(WorkComment::getGmtCreated);
        page(rootPage, rootWrapper);

        List<WorkComment> roots = rootPage.getRecords();
        Page<WorkCommentVO> result = new Page<>(rootPage.getCurrent(), rootPage.getSize(), rootPage.getTotal());
        if (roots == null || roots.isEmpty()) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        List<Long> rootIds = roots.stream().map(WorkComment::getId).collect(Collectors.toList());
        List<WorkComment> replies = list(new LambdaQueryWrapper<WorkComment>()
                .in(WorkComment::getRootId, rootIds)
                .ne(WorkComment::getParentId, 0L)
                .eq(WorkComment::getAuditStatus, AuditStatus.SUCCESS.code())
                .orderByAsc(WorkComment::getGmtCreated));

        List<WorkComment> all = new ArrayList<>(roots);
        all.addAll(replies);
        Map<Long, User> users = loadUsers(all);
        Map<Long, List<WorkComment>> repliesByRoot = replies.stream()
                .collect(Collectors.groupingBy(WorkComment::getRootId, LinkedHashMap::new, Collectors.toList()));

        List<WorkCommentVO> records = new ArrayList<>();
        for (WorkComment root : roots) {
            WorkCommentVO rootVO = toVO(root, users);
            for (WorkComment reply : repliesByRoot.getOrDefault(root.getId(), Collections.emptyList())) {
                rootVO.getReplies().add(toVO(reply, users));
            }
            records.add(rootVO);
        }
        result.setRecords(records);
        return result;
    }

    private Map<Long, User> loadUsers(List<WorkComment> comments) {
        Set<Long> ids = new HashSet<>();
        for (WorkComment comment : comments) {
            ids.add(comment.getUserId());
            if (comment.getReplyToUserId() != null) {
                ids.add(comment.getReplyToUserId());
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userService.list(ids).stream().collect(Collectors.toMap(User::getId, user -> user));
    }

    private WorkCommentVO toVO(WorkComment comment, Map<Long, User> users) {
        WorkCommentVO vo = new WorkCommentVO();
        vo.setId(comment.getId());
        vo.setWorkId(comment.getWorkId());
        vo.setUserId(comment.getUserId());
        vo.setParentId(comment.getParentId());
        vo.setRootId(comment.getRootId());
        vo.setReplyToUserId(comment.getReplyToUserId());
        vo.setContent(comment.getContent());
        vo.setGmtCreated(comment.getGmtCreated());
        User user = users.get(comment.getUserId());
        if (user != null) {
            vo.setUserName(user.getNickName());
            vo.setUserAvatar(user.getAvatar());
        }
        User replyToUser = users.get(comment.getReplyToUserId());
        if (replyToUser != null) {
            vo.setReplyToUserName(replyToUser.getNickName());
        }
        return vo;
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        WorkComment comment = getById(id);
        if (comment == null || !Objects.equals(comment.getUserId(), userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "评论不存在或无权删除");
        }
        if (Objects.equals(comment.getParentId(), 0L)) {
            remove(new LambdaQueryWrapper<WorkComment>()
                    .eq(WorkComment::getRootId, comment.getRootId()));
        } else {
            removeById(id);
        }
    }

    @Override
    public IPage<WorkComment> pageForAudit(BasePage basePage, Integer auditStatus) {
        LambdaQueryWrapper<WorkComment> wrapper = new LambdaQueryWrapper<WorkComment>()
                .eq(auditStatus != null, WorkComment::getAuditStatus, auditStatus)
                .orderByAsc(WorkComment::getAuditStatus)
                .orderByDesc(WorkComment::getGmtCreated);
        return page(basePage.page(), wrapper);
    }

    @Override
    @Transactional
    public void audit(CommentAuditRequest request) {
        if (!Objects.equals(request.getAuditStatus(), AuditStatus.SUCCESS.code())
                && !Objects.equals(request.getAuditStatus(), AuditStatus.PASS.code())) {
            throw new AppException(ErrorCode.INVALID_AUDIT_STATUS);
        }
        WorkComment comment = getById(request.getId());
        if (comment == null) {
            throw new AppException(ErrorCode.COMMENT_NOT_FOUND);
        }
        Integer oldStatus = comment.getAuditStatus();
        comment.setAuditStatus(request.getAuditStatus());
        comment.setAuditMsg(StringUtils.hasText(request.getAuditMsg())
                ? request.getAuditMsg() : (Objects.equals(request.getAuditStatus(), AuditStatus.SUCCESS.code()) ? "通过" : "人工审核未通过"));
        updateById(comment);
        commentAuditService.publishAuditResult(comment, oldStatus);
    }

    @Override
    public List<WorkComment> listByRootIds(List<Long> rootIds) {
        if (rootIds == null || rootIds.isEmpty()) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<WorkComment>()
                .in(WorkComment::getRootId, rootIds)
                .eq(WorkComment::getAuditStatus, AuditStatus.SUCCESS.code())
                .orderByAsc(WorkComment::getGmtCreated));
    }
}
