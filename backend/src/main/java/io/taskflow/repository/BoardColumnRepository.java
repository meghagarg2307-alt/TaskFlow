package io.taskflow.repository;

import io.taskflow.domain.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, UUID> {

    @Query("""
            select c from BoardColumn c
             where c.board.id = :boardId
               and c.organization.id = :organizationId
               and c.deletedAt is null
             order by c.position asc
            """)
    List<BoardColumn> findOrderedByBoard(UUID boardId, UUID organizationId);

    @Query("""
            select c from BoardColumn c
              join fetch c.board b
              join fetch b.project
             where c.id = :id
               and c.organization.id = :organizationId
               and c.deletedAt is null
            """)
    Optional<BoardColumn> findByIdAndOrg(UUID id, UUID organizationId);

    /** Max position in a board — used when appending a new column. */
    @Query("""
            select coalesce(max(c.position), 0)
              from BoardColumn c
             where c.board.id = :boardId
               and c.deletedAt is null
            """)
    long maxPositionInBoard(UUID boardId);
}
