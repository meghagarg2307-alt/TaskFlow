package io.taskflow.service.trash;

import io.taskflow.domain.enums.TrashResourceType;
import io.taskflow.dto.trash.TrashItemResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface TrashService {

    Page<TrashItemResponse> listTrash(TrashResourceType type, int page, int size);

    void restore(TrashResourceType type, UUID id);

    void permanentlyDelete(TrashResourceType type, UUID id);
}
