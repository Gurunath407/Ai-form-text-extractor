package com.task.hwai.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class HttpClientConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(300, TimeUnit.SECONDS)  // Connection timeout
                .readTimeout(300, TimeUnit.SECONDS)     // Read timeout
                .writeTimeout(300, TimeUnit.SECONDS)    // Write timeout
                .retryOnConnectionFailure(true)
                .build();
    }
}
