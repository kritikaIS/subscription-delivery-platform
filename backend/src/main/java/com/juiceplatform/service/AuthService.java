package com.juiceplatform.service;

import com.juiceplatform.dto.auth.AdminLoginRequest;
import com.juiceplatform.dto.auth.AuthResponse;
import com.juiceplatform.dto.auth.CustomerGoogleLoginRequest;
import com.juiceplatform.dto.auth.CustomerLoginResponse;
import com.juiceplatform.dto.auth.RefreshTokenRequest;

public interface AuthService {

    CustomerLoginResponse customerGoogleLogin(CustomerGoogleLoginRequest request);

    AuthResponse adminLogin(AdminLoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);
}
