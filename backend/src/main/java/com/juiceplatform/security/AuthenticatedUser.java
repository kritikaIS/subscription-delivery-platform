package com.juiceplatform.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Represents the authenticated principal extracted from a valid JWT.
 * Contains only stable identity claims — no mutable business state.
 * Used via @AuthenticationPrincipal in controllers.
 */
@Getter
@AllArgsConstructor
public class AuthenticatedUser {

    private UUID userId;
    private String role;
    private String phone;
}
