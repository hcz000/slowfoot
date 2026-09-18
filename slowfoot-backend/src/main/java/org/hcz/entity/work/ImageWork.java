package org.hcz.entity.work;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


// Image-specific marker. Image files 是 存储的  work_asset.

@Data
@TableName("image_work")
public class ImageWork implements Serializable {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
}
