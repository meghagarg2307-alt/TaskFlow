package io.taskflow.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "taskflow.websocket")
public record WebSocketProperties(
        @NotNull List<String> allowedOrigins,
        @Positive long heartbeatIntervalMs
) {}
