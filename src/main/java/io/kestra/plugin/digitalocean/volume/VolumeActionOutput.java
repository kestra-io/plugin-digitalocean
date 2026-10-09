package io.kestra.plugin.digitalocean.volume;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asLong;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asMap;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

@Builder
@Getter
public class VolumeActionOutput implements Output {

    @Schema(title = "Action ID")
    private final Long actionId;

    @Schema(title = "Action status", description = "Current status of the action: in-progress, completed, or errored.")
    private final String status;

    @Schema(title = "Action type", description = "Type of the action: attach_volume, detach_volume, or resize_volume.")
    private final String type;

    @Schema(title = "Start timestamp")
    private final Instant startedAt;

    @Schema(title = "Completion timestamp", description = "Null while the action is still in progress.")
    private final Instant completedAt;

    @Schema(title = "Region slug", description = "Datacenter region the action ran in, e.g. nyc3.")
    private final String region;

    public static VolumeActionOutput from(Map<String, Object> action) {
        var startedAt = asString(action.get("started_at"));
        var completedAt = asString(action.get("completed_at"));
        var region = asString(action.get("region_slug"));
        if (region == null) {
            var regionObject = asMap(action.get("region"));
            region = regionObject != null ? asString(regionObject.get("slug")) : null;
        }

        return VolumeActionOutput.builder()
            .actionId(asLong(action.get("id")))
            .status(asString(action.get("status")))
            .type(asString(action.get("type")))
            .startedAt(startedAt != null ? Instant.parse(startedAt) : null)
            .completedAt(completedAt != null ? Instant.parse(completedAt) : null)
            .region(region)
            .build();
    }
}
