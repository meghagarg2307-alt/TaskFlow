package io.taskflow.service.trash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrashPurgeScheduler {

    private final TrashPurgeService purgeService;

    /** Daily at 03:00 UTC — permanently remove trash older than retention policy. */
    @Scheduled(cron = "${taskflow.trash.purge-cron:0 0 3 * * *}", zone = "UTC")
    public void purgeExpiredTrash() {
        try {
            purgeService.purgeExpired();
        } catch (Exception ex) {
            log.error("Scheduled trash purge failed", ex);
        }
    }
}
