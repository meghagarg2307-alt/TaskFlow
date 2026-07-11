package io.taskflow.controller;

import io.taskflow.domain.enums.TrashResourceType;
import io.taskflow.dto.trash.TrashItemResponse;
import io.taskflow.service.trash.TrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public Page<TrashItemResponse> list(
            @RequestParam(required = false) TrashResourceType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return trashService.listTrash(type, page, size);
    }

    @PostMapping("/{type}/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restore(@PathVariable TrashResourceType type,
                                        @PathVariable UUID id) {
        trashService.restore(type, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{type}/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> permanentlyDelete(@PathVariable TrashResourceType type,
                                                  @PathVariable UUID id) {
        trashService.permanentlyDelete(type, id);
        return ResponseEntity.noContent().build();
    }
}
