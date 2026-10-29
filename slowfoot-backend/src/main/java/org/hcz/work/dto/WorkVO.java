package org.hcz.work.dto;

import org.hcz.audit.dto.WorkAuditVO;

import org.hcz.user.dto.UserVO;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import org.hcz.work.entity.Work;
import org.hcz.work.entity.WorkAssetEntity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;


// 作品对外载荷。
//
// 展示字段（assets / 用户 / format / typeName / likeState / favoriteState / followState）
// 不进 {@link Work}：Work 同时是管理端与内部链路的实体，把「当前请求者视角」的状态塞进去
// 会变成面向其他调用方的永久 null。本类与 WorkAuditVO 遵循同一条规则。

@Data
public class WorkVO implements Serializable {

    private static final long serialVersionUID = 1L;


// 以下字段与 work 表列一一对应。

    // Work.id uses MyBatis-Plus ASSIGN_ID (Snowflake); keep it exact in JavaScript.
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String workNo;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String workType;
    private Long primaryFileId;
    private Long coverFileId;
    private String title;
    private String description;
    private String content;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long typeId;
    private String labelNames;
    private Boolean openState;
    private Integer auditStatus;
    private String auditMsg;
    private String duration;
    private Long likeCount;
    private Long shareCount;
    private Long viewCount;
    private Long favoriteCount;
    private Date gmtCreated;


// 以下字段只用于展示。

    private List<WorkAssetEntity> assets;
    private UserVO user;

// 主文件的 MIME/格式，前端 <source type=...> 播放时使用。

    private String format;
    private String typeName;

// 当前用户是否点过赞 / 收过藏 / 关注过作者。

    private Boolean likeState;
    private Boolean favoriteState;
    private Boolean followState;


// 唯一转换入口：镜像字段从这里一次拷全，其余任何地方不要重复手写。

    public static WorkVO of(Work work) {
        if (work == null) {
            return null;
        }
        WorkVO vo = new WorkVO();
        vo.setId(work.getId());
        vo.setWorkNo(work.getWorkNo());
        vo.setUserId(work.getUserId());
        vo.setWorkType(work.getWorkType());
        vo.setPrimaryFileId(work.getPrimaryFileId());
        vo.setCoverFileId(work.getCoverFileId());
        vo.setTitle(work.getTitle());
        vo.setDescription(work.getDescription());
        vo.setContent(work.getContent());
        vo.setTypeId(work.getTypeId());
        vo.setLabelNames(work.getLabelNames());
        vo.setOpenState(work.getOpenState());
        vo.setAuditStatus(work.getAuditStatus());
        vo.setAuditMsg(work.getAuditMsg());
        vo.setDuration(work.getDuration());
        vo.setLikeCount(work.getLikeCount());
        vo.setShareCount(work.getShareCount());
        vo.setViewCount(work.getViewCount());
        vo.setFavoriteCount(work.getFavoriteCount());
        vo.setGmtCreated(work.getGmtCreated());
        vo.setAssets(work.getAssets());
        return vo;
    }
}
