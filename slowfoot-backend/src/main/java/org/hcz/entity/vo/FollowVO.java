package org.hcz.entity.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.entity.user.Follow;



@Data
@EqualsAndHashCode(callSuper = false)
public class FollowVO extends Follow {

    private String nickName;
}
