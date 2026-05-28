package com.juiceplatform.controller;

import com.juiceplatform.dto.auth.AdminLoginRequest;
import com.juiceplatform.dto.auth.AuthResponse;
import com.juiceplatform.dto.auth.CustomerGoogleLoginRequest;
import com.juiceplatform.dto.auth.CustomerLoginResponse;
import com.juiceplatform.dto.auth.RefreshTokenRequest;
import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/customer/google")
    public ResponseEntity<ApiResponse<CustomerLoginResponse>> customerGoogleLogin(
            @RequestBody @Valid CustomerGoogleLoginRequest request) {

        CustomerLoginResponse response = authService.customerGoogleLogin(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/customer/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> customerRefresh(
            @RequestBody @Valid RefreshTokenRequest request) {

        AuthResponse response = authService.refreshToken(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<AuthResponse>> adminLogin(
            @RequestBody @Valid AdminLoginRequest request) {

        AuthResponse response = authService.adminLogin(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/admin/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> adminRefresh(
            @RequestBody @Valid RefreshTokenRequest request) {

        AuthResponse response = authService.refreshToken(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody @Valid RefreshTokenRequest request) {

        authService.logout(request);

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
