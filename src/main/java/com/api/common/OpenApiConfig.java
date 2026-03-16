package com.api.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("mei-organizadin API")
                        .version("1.0.0")
                        .description("API backend para acompanhamento de faturamento e fluxo de caixa " +
                                "a partir de agendamentos do Google Agenda. " +
                                "Autenticação via Google ID Token como Bearer."))
                .addSecurityItem(new SecurityRequirement().addList("bearer-token"))
                .schemaRequirement("bearer-token", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("Google ID Token")
                        .description("Insira o Google ID Token obtido pelo app Android"));
    }
}
