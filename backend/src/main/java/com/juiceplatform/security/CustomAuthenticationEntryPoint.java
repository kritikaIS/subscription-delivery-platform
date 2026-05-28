package com.juiceplatform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juiceplatform.dto.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns 401 Unauthorized in the standard API error envelope format
 * when a request lacks valid authentication credentials.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                "UNAUTHORIZED",
                "Missing, invalid, or expired authentication token"
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
