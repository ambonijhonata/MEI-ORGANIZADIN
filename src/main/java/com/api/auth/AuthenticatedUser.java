package com.api.auth;

public record AuthenticatedUser(
        Long userId,
        String googleSub,
        String email,
        String name
) {}
