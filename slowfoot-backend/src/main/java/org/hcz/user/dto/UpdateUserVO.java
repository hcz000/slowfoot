package org.hcz.user.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;



@Data
public class UpdateUserVO {

    @NotBlank(message = "昵称不可为空")
    private String nickName;

    private Long avatar;

    private Boolean sex;

    private String description;

    private Long defaultFavoritesId;


}
