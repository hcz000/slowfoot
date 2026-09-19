package org.hcz.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;


// 管理收藏夹下的作品，对应 favorites_work 表。
//

@Data
@EqualsAndHashCode(callSuper = false)
public class FavoritesWork implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long favoritesId;

    private Long workId;

    // 冗余字段
    private Long userId;

}
