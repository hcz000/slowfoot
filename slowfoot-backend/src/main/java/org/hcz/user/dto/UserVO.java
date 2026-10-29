package org.hcz.user.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;



@Data
public class UserVO{

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String nickName;

    private Long avatar;

    private Boolean sex;

    private String description;

    private Long follow;

    private Long fans;
}
