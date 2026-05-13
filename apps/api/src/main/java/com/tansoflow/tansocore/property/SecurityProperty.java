package com.tansoflow.tansocore.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security")
@Data
public class SecurityProperty {
    private Jwt jwt = new Jwt();

    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenTtlSeconds;
        private String issuer;
    }
}


