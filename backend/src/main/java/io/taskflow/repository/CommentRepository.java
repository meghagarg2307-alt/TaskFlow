package io.taskflow.repository;

import io.taskflow.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query("""
            select c from Comment c
              join fetch c.author
             where c.task.id = :taskId
               and c.organization.id = :organizationId
               and c.deletedAt is null
             order by c.createdAt asc
            """)
    List<Comment> findActiveByTask(UUID taskId, UUID organizationId);

    @Query("""
            select c from Comment c
             where c.id = :id
               and c.organization.id = :organizationId
               and c.deletedAt is null
            """)
    Optional<Comment> findActiveByIdAndOrg(UUID id, UUID organizationId);
}
