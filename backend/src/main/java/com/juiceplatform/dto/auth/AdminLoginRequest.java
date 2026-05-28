package com.juiceplatform.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginRequest {

    @NotBlank
    private String phone;

    @NotBlank
    private String password;
}
