package com.tansoflow.tansocore.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi clientApi() {
        return GroupedOpenApi.builder()
                .group("Client API")
                .pathsToMatch("/api/v1/client/**")
                .build();
    }

    @Bean
    public GroupedOpenApi tansoApi() {
        return GroupedOpenApi.builder()
                .group("Tanso API")
                .pathsToExclude("/api/v1/client/**")
                .build();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Tanso API").version("v1"))
                .components(new Components()
                        .addSecuritySchemes("Bearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("Authorization")
                                        .description("Use format: `Authorization: Bearer <api_key>`")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }
}

