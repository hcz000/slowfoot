package org.hcz.entity.history;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user_search_history")
public class SearchHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String keyword;
    private Date searchedAt;
}
