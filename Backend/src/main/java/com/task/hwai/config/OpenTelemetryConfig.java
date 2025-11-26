package com.task.hwai.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
        try {
            // Get Langfuse OTLP endpoint from environment
            String endpoint = System.getenv().getOrDefault(
                    "LANGFUSE_OTLP_ENDPOINT", "https://cloud.langfuse.com/api/public/otel"
            );
            
            String publicKey = System.getenv("LANGFUSE_PUBLIC_KEY");
            String secretKey = System.getenv("LANGFUSE_SECRET_KEY");
            
            // Debug logging
            System.out.println("[LANGFUSE] Initializing OpenTelemetry with endpoint: " + endpoint);
            System.out.println("[LANGFUSE] Public Key: " + (publicKey != null ? publicKey.substring(0, Math.min(10, publicKey.length())) + "..." : "null"));
            
            // Create Basic Auth header value
            String credentials = publicKey + ":" + secretKey;
            String basicAuth = "Basic " + java.util.Base64.getEncoder().encodeToString(credentials.getBytes());

            OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                    .setEndpoint(endpoint)
                    .addHeader("Authorization", basicAuth)
                    .build();
            
            System.out.println("[LANGFUSE] OpenTelemetry exporter configured successfully");

            Resource resource = Resource.getDefault().toBuilder()
                    .put(AttributeKey.stringKey("service.name"), "handwrite-ai")
                    .build();

            SdkTracerProvider provider = SdkTracerProvider.builder()
                    .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                    .setResource(resource)
                    .build();

            OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(provider)
                    .build();

            GlobalOpenTelemetry.set(sdk);
            return sdk;
        } catch (Throwable t) {
            System.err.println("OpenTelemetry initialization failed, falling back to noop: " + t.getMessage());
            return GlobalOpenTelemetry.get();
        }
    }

    @Bean
    public Tracer tracer(OpenTelemetry otel) {
        return otel.getTracer("com.example.hwai");
    }
}
