package io.taskflow.service.board;

import io.taskflow.dto.board.BoardSnapshot;
import io.taskflow.dto.board.BoardSummary;
import io.taskflow.dto.board.CreateBoardRequest;
import io.taskflow.dto.board.CreateColumnRequest;
import io.taskflow.dto.board.UpdateBoardRequest;
import io.taskflow.dto.board.UpdateColumnRequest;

import java.util.List;
import java.util.UUID;

public interface BoardService {

    BoardSummary createBoard(UUID projectId, CreateBoardRequest request);
    List<BoardSummary> listBoardsInProject(UUID projectId);
    BoardSnapshot getBoardSnapshot(UUID boardId);
    BoardSummary updateBoard(UUID boardId, UpdateBoardRequest request);
    void deleteBoard(UUID boardId);

    BoardSnapshot.ColumnView createColumn(UUID boardId, CreateColumnRequest request);
    BoardSnapshot.ColumnView updateColumn(UUID columnId, UpdateColumnRequest request);
    void deleteColumn(UUID columnId);
}
