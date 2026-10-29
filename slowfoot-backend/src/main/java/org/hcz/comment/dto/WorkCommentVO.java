package org.hcz.comment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class WorkCommentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String userName;
    private Long userAvatar;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long rootId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyToUserId;
    private String replyToUserName;
    private String content;
    private Date gmtCreated;
    private List<WorkCommentVO> replies = new ArrayList<>();
}
