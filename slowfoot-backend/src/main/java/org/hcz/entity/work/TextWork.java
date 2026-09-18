package org.hcz.entity.work;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


// Text-specific 字段. Its ID 是 该 global 作品 ID.

@Data
@TableName("text_work")
public class TextWork implements Serializable {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String content;
}
