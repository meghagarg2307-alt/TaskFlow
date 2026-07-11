package io.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.trash")
public record TrashProperties(
        /** Days a deleted item remains recoverable before permanent purge. */
        int retentionDays
) {
    public TrashProperties {
        if (retentionDays <= 0) {
            retentionDays = 30;
        }
    }
}
