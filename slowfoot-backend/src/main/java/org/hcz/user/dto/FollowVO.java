package org.hcz.user.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.user.entity.Follow;



@Data
@EqualsAndHashCode(callSuper = false)
public class FollowVO extends Follow {

    private String nickName;
}
