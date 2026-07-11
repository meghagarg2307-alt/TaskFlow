package io.taskflow.dto.task;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.taskflow.domain.enums.TaskPriority;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Explicit deserializer for patch-style {@link UpdateTaskRequest}.
 *
 * <p>Jackson's default handling of {@code Optional} on Java records is unreliable for
 * the three-state contract we need: absent field = leave alone, JSON {@code null} =
 * clear, string UUID = set. A dedicated deserializer keeps assignee updates from
 * failing with 500s on otherwise valid PATCH bodies.</p>
 */
public class UpdateTaskRequestDeserializer extends JsonDeserializer<UpdateTaskRequest> {

    @Override
    public UpdateTaskRequest deserialize(JsonParser parser, DeserializationContext ctxt)
            throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);

        String title = textOrNull(node, "title");
        String description = textOrNull(node, "description");
        TaskPriority priority = node.hasNonNull("priority")
                ? TaskPriority.valueOf(node.get("priority").asText())
                : null;
        Optional<UUID> assigneeId = optionalUuid(node, "assigneeId");
        Optional<Instant> dueDate = optionalInstant(node, "dueDate");
        Long expectedVersion = node.hasNonNull("expectedVersion")
                ? node.get("expectedVersion").asLong()
                : null;

        return new UpdateTaskRequest(title, description, priority, assigneeId, dueDate, expectedVersion);
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    /**
     * @return {@code null} if the field is absent, {@link Optional#empty()} if JSON null,
     *         otherwise {@link Optional#of(Object)}.
     */
    private static Optional<UUID> optionalUuid(JsonNode node, String field) {
        if (!node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isNull()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(value.asText()));
    }

    private static Optional<Instant> optionalInstant(JsonNode node, String field) {
        if (!node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isNull()) {
            return Optional.empty();
        }
        return Optional.of(Instant.parse(value.asText()));
    }
}
