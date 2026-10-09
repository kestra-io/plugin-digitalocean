package io.kestra.plugin.digitalocean.spaces.key;

import io.kestra.core.models.tasks.Output;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

@Builder
@Getter
public class KeyOutput implements Output {

    @Schema(title = "Key name")
    private final String name;

    @Schema(title = "Access key ID", description = "The access key ID used to access the buckets, e.g. DO00EXAMPLE.")
    private final String accessKey;

    @Schema(title = "Grants", description = "Bucket permissions of the key, each with a bucket and a permission (read, readwrite, or fullaccess).")
    private final List<Map<String, String>> grants;

    @Schema(title = "Creation timestamp")
    private final Instant createdAt;

    public static KeyOutput from(Map<String, Object> key) {
        var createdAt = asString(key.get("created_at"));

        return KeyOutput.builder()
            .name(asString(key.get("name")))
            .accessKey(asString(key.get("access_key")))
            .grants(grants(key.get("grants")))
            .createdAt(createdAt != null ? Instant.parse(createdAt) : null)
            .build();
    }

    static List<Map<String, String>> grants(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .map(AbstractDigitalOceanTask::asMap)
            .filter(Objects::nonNull)
            .map(grant -> Map.of(
                "bucket", Objects.requireNonNullElse(asString(grant.get("bucket")), ""),
                "permission", Objects.requireNonNullElse(asString(grant.get("permission")), "")
            ))
            .toList();
    }
}
