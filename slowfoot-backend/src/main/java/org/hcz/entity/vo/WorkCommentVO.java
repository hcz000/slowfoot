package org.hcz.entity.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class WorkCommentVO {

    private Long id;
    private Long workId;
    private Long userId;
    private String userName;
    private Long userAvatar;
    private Long parentId;
    private Long rootId;
    private Long replyToUserId;
    private String replyToUserName;
    private String content;
    private Date gmtCreated;
    private List<WorkCommentVO> replies = new ArrayList<>();
}
