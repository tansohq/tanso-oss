package com.tansoflow.tansocore.property;

import lombok.Data;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;

@EnableAutoConfiguration
@EnableConfigurationProperties
@Component
@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperty {
    private String stripeWebhookEndpoint;
    private boolean dogfoodingEnabled = false;
    private String masterAccountId = "00000000-0000-0000-0000-000000000000";
    private List<String> corsAllowedOrigins = List.of();
    private String apiKeyPrefix;
    private String defaultFreePlanId;

}
