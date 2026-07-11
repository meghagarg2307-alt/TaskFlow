package io.taskflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * <p>{@code @EnableJpaAuditing} wires up {@code @CreatedDate}/{@code @LastModifiedDate}
 * and our {@code AuditorAware} (defined in JpaAuditingConfig) which records the acting
 * user on every write — crucial for the activity feed and accountability.</p>
 *
 * <p>{@code @EnableAsync} is used by the websocket fan-out path so REST controllers
 * never block on Redis pub/sub publication.</p>
 */
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableAsync
@EnableScheduling
public class TaskflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskflowApplication.class, args);
    }
}
