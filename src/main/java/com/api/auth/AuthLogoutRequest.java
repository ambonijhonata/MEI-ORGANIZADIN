package com.api.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthLogoutRequest(@NotBlank String refreshToken) {}
