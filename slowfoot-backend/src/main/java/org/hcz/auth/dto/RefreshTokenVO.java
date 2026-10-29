package org.hcz.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenVO {

    @NotBlank
    private String refreshToken;
}
