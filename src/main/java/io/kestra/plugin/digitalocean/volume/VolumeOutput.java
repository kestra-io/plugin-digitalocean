package io.kestra.plugin.digitalocean.volume;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asLong;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asMap;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

/** Shared output shape for {@link Get} and {@link Create}: both return the same volume JSON object. */
@Builder
@Getter
public class VolumeOutput implements Output {

    @Schema(title = "Volume ID", description = "UUID assigned by DigitalOcean.")
    private final String id;

    @Schema(title = "Volume name")
    private final String name;

    @Schema(title = "Region slug", description = "Datacenter region the volume lives in, e.g. nyc1.")
    private final String region;

    @Schema(title = "Size in GiB")
    private final Long sizeGigabytes;

    @Schema(title = "Filesystem type", description = "One of ext4 or xfs, if the volume was formatted at creation time.")
    private final String filesystemType;

    public static VolumeOutput from(Map<String, Object> volume) {
        var region = asMap(volume.get("region"));

        return VolumeOutput.builder()
            .id(asString(volume.get("id")))
            .name(asString(volume.get("name")))
            .region(region != null ? asString(region.get("slug")) : null)
            .sizeGigabytes(asLong(volume.get("size_gigabytes")))
            .filesystemType(asString(volume.get("filesystem_type")))
            .build();
    }
}
