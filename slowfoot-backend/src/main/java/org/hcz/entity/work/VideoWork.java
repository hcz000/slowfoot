package org.hcz.entity.work;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


// 视频作品专有字段，id 即全局 work.id。

@Data
@TableName("video_work")
public class VideoWork implements Serializable {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long primaryFileId;
    private Long coverFileId;
    private String duration;
}
