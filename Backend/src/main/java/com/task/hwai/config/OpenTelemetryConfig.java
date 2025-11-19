package com.task.hwai.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
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
            String endpoint = System.getenv().getOrDefault(
                    "LANGFUSE_OTLP_ENDPOINT", "http://localhost:4317"
            );

            OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(endpoint)
                    .addHeader("x-langfuse-public-key", System.getenv("LANGFUSE_PUBLIC_KEY"))
                    .addHeader("x-langfuse-secret-key", System.getenv("LANGFUSE_SECRET_KEY"))
                    .build();

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
