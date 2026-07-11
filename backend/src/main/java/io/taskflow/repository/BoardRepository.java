package io.taskflow.repository;

import io.taskflow.domain.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {

    @Query("""
            select b from Board b
              join fetch b.project
             where b.project.id = :projectId
               and b.organization.id = :organizationId
               and b.deletedAt is null
             order by b.createdAt desc
            """)
    List<Board> findAllActiveInProject(UUID projectId, UUID organizationId);

    @Query("""
            select b from Board b
              join fetch b.project
             where b.id = :id
               and b.organization.id = :organizationId
               and b.deletedAt is null
            """)
    Optional<Board> findActiveByIdAndOrg(UUID id, UUID organizationId);

    @Query("""
            select b from Board b
              join fetch b.project
             where b.organization.id = :organizationId
               and b.deletedAt is not null
               and b.project.deletedAt is null
             order by b.deletedAt desc
            """)
    List<Board> findDeletedRoots(UUID organizationId);

    @Query("""
            select b from Board b
              join fetch b.project
             where b.id = :id
               and b.organization.id = :organizationId
               and b.deletedAt is not null
            """)
    Optional<Board> findDeletedByIdAndOrg(UUID id, UUID organizationId);

    @Query("""
            select b from Board b
              join fetch b.organization
             where b.deletedAt is not null
               and b.deletedAt < :cutoff
               and b.organization.deletedAt is null
               and b.project.deletedAt is null
            """)
    List<Board> findExpiredDeletedBoards(Instant cutoff);
}
