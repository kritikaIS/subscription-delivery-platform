package com.juiceplatform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juiceplatform.dto.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns 403 Forbidden in the standard API error envelope format
 * when an authenticated user lacks permission for the requested resource.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                "FORBIDDEN",
                "Authenticated user lacks permission for this resource"
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
