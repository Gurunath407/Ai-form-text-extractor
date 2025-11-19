package com.task.hwai.config;

import com.theokanning.openai.OpenAiService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangchainConfig {

    @Bean
    public OpenAiService openAiService() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable must be set and non-empty");
        }
        return new OpenAiService(apiKey);
    }
}
