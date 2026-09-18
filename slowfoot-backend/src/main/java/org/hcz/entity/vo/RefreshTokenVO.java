package org.hcz.entity.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenVO {

    @NotBlank
    private String refreshToken;
}
