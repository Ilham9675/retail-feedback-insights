package com.retailstore.feedback.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title and description shown in Swagger UI ({@code /swagger-ui.html}).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI feedbackOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Retail Feedback Insights API")
                .version("0.1.0")
                .description("Customer reviews analysed with Google Gemini (sentiment, category, actionable insight) "
                        + "and Stanford CoreNLP, with accuracy against labelled data and per-department executive summaries.")
                .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
