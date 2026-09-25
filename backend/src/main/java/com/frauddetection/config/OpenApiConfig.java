package com.frauddetection.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fraudDetectionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fraud Detection & Risk Assessment Platform")
                        .description("Transaction ingestion, Redis-based velocity tracking, "
                                + "and ML-powered fraud risk scoring.")
                        .version("1.0.0"));
    }
}
