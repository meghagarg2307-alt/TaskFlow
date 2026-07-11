package io.taskflow.service.trash;

import io.taskflow.config.TrashProperties;
import io.taskflow.domain.Organization;
import io.taskflow.domain.Project;
import io.taskflow.repository.BoardRepository;
import io.taskflow.repository.OrganizationRepository;
import io.taskflow.repository.ProjectRepository;
import io.taskflow.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrashPurgeService {

    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final SoftDeleteCascadeService cascade;
    private final TrashProperties trashProperties;

    @Transactional
    public int purgeExpired() {
        Instant cutoff = Instant.now().minus(trashProperties.retentionDays(), ChronoUnit.DAYS);
        int count = 0;

        List<Organization> orgs = organizationRepository.findDeletedBefore(cutoff);
        for (Organization org : orgs) {
            log.info("Permanently purging expired workspace {}", org.getId());
            cascade.permanentlyDeleteOrganization(org.getId());
            count++;
        }

        for (Project p : projectRepository.findExpiredDeletedProjects(cutoff)) {
            log.info("Permanently purging expired project {}", p.getId());
            cascade.permanentlyDeleteProject(p.getId(), p.getOrganization().getId());
            count++;
        }

        for (var b : boardRepository.findExpiredDeletedBoards(cutoff)) {
            log.info("Permanently purging expired board {}", b.getId());
            cascade.permanentlyDeleteBoard(b.getId(), b.getOrganization().getId());
            count++;
        }

        for (var t : taskRepository.findExpiredDeletedTasks(cutoff)) {
            log.info("Permanently purging expired task {}", t.getId());
            cascade.permanentlyDeleteTask(t.getId(), t.getOrganization().getId());
            count++;
        }

        if (count > 0) {
            log.info("Trash purge completed: {} root resource(s) removed", count);
        }
        return count;
    }
}
