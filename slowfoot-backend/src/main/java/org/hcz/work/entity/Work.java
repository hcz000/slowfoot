package org.hcz.work.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.common.entity.BaseEntity;
import org.hcz.common.util.OssUrlUtil;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


// 统一作品实体，对应 work 表。

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work")
public class Work extends BaseEntity {

    private String workNo;
    private Long userId;
    private String workType;

// 作品的主资源，完整资源仍通过 work_asset 管理。

    private Long primaryFileId;
    private String title;
    private String description;
    @TableField(exist = false)
    private String content;
    private Long typeId;
    private String labelNames;
    private Long coverFileId;
    private Boolean openState;
    private Integer auditStatus;
    private String auditMsg;

// 审核结果通知是否已发送，用于幂等。

    private Boolean auditNotificationSent;

// 视频时长，图片/文字作品为空。

    private String duration;
    private Long likeCount;
    private Long shareCount;
    private Long viewCount;
    private Long favoriteCount;

    @TableField(exist = false)
    private List<WorkAssetEntity> assets;


// 拆分逗号分隔的标签；展示字段不落库，仍留在本实体上供审核与推荐链路使用。

    public List<String> buildLabel() {
        if (ObjectUtils.isEmpty(this.labelNames)) return Collections.emptyList();
        return Arrays.asList(this.labelNames.split(","));
    }

    // 和get方法分开，避免发生歧义
    public String buildPrimaryFileUrl() {
        return OssUrlUtil.buildUrl(this.primaryFileId);
    }

    public String buildCoverUrl() {
        return OssUrlUtil.buildUrl(this.coverFileId);
    }
}
