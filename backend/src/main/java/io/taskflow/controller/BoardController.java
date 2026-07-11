package io.taskflow.controller;

import io.taskflow.dto.board.BoardSnapshot;
import io.taskflow.dto.board.BoardSummary;
import io.taskflow.dto.board.CreateBoardRequest;
import io.taskflow.dto.board.CreateColumnRequest;
import io.taskflow.dto.board.UpdateBoardRequest;
import io.taskflow.dto.board.UpdateColumnRequest;
import io.taskflow.service.board.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    // ------------------------------------------------- boards under a project

    @PostMapping("/projects/{projectId}/boards")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<BoardSummary> createBoard(@PathVariable UUID projectId,
                                                    @Valid @RequestBody CreateBoardRequest req) {
        BoardSummary board = boardService.createBoard(projectId, req);
        return ResponseEntity.created(URI.create("/boards/" + board.id())).body(board);
    }

    @GetMapping("/projects/{projectId}/boards")
    public List<BoardSummary> listBoards(@PathVariable UUID projectId) {
        return boardService.listBoardsInProject(projectId);
    }

    // ------------------------------------------------------------------ board

    @GetMapping("/boards/{boardId}")
    public BoardSnapshot getBoard(@PathVariable UUID boardId) {
        return boardService.getBoardSnapshot(boardId);
    }

    @PatchMapping("/boards/{boardId}")
    @PreAuthorize("hasRole('MANAGER')")
    public BoardSummary updateBoard(@PathVariable UUID boardId,
                                    @Valid @RequestBody UpdateBoardRequest req) {
        return boardService.updateBoard(boardId, req);
    }

    @DeleteMapping("/boards/{boardId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteBoard(@PathVariable UUID boardId) {
        boardService.deleteBoard(boardId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- columns

    @PostMapping("/boards/{boardId}/columns")
    @PreAuthorize("hasRole('MEMBER')")
    public BoardSnapshot.ColumnView createColumn(@PathVariable UUID boardId,
                                                 @Valid @RequestBody CreateColumnRequest req) {
        return boardService.createColumn(boardId, req);
    }

    @PatchMapping("/columns/{columnId}")
    @PreAuthorize("hasRole('MEMBER')")
    public BoardSnapshot.ColumnView updateColumn(@PathVariable UUID columnId,
                                                 @Valid @RequestBody UpdateColumnRequest req) {
        return boardService.updateColumn(columnId, req);
    }

    @DeleteMapping("/columns/{columnId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteColumn(@PathVariable UUID columnId) {
        boardService.deleteColumn(columnId);
        return ResponseEntity.noContent().build();
    }
}
