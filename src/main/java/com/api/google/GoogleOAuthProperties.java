package com.api.google;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "google.oauth")
public record GoogleOAuthProperties(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String tokenUri,
        @NotBlank String authUri
) {
    @Override
    public String toString() {
        return "GoogleOAuthProperties[clientId=" + clientId + ", tokenUri=" + tokenUri + ", authUri=" + authUri + "]";
    }
}
