package io.kestra.plugin.digitalocean.droplet;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asLong;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asMap;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

/** Shared output shape for {@link Get} and {@link Create}: both return the same droplet JSON object. */
@Builder
@Getter
public class DropletOutput implements Output {

    @Schema(title = "Droplet ID")
    private final Long id;

    @Schema(title = "Droplet name")
    private final String name;

    @Schema(title = "Droplet status", description = "One of new, active, off, or archive.")
    private final String status;

    @Schema(title = "Region slug", description = "Datacenter region the droplet runs in, e.g. nyc3.")
    private final String region;

    @Schema(title = "Size slug", description = "Droplet size slug, e.g. s-1vcpu-1gb.")
    private final String sizeSlug;

    @Schema(title = "Public IPv4 address", description = "First public IPv4 address assigned to the droplet, if any.")
    private final String ip;

    @Schema(title = "Creation timestamp")
    private final Instant createdAt;

    public static DropletOutput from(Map<String, Object> droplet) {
        var region = asMap(droplet.get("region"));
        var createdAt = asString(droplet.get("created_at"));

        return DropletOutput.builder()
            .id(asLong(droplet.get("id")))
            .name(asString(droplet.get("name")))
            .status(asString(droplet.get("status")))
            .region(region != null ? asString(region.get("slug")) : null)
            .sizeSlug(asString(droplet.get("size_slug")))
            .ip(publicIpv4(droplet))
            .createdAt(createdAt != null ? Instant.parse(createdAt) : null)
            .build();
    }

    private static String publicIpv4(Map<String, Object> droplet) {
        var networks = asMap(droplet.get("networks"));
        if (networks == null || !(networks.get("v4") instanceof List<?> addresses)) {
            return null;
        }

        for (var entry : addresses) {
            var address = asMap(entry);
            if (address != null && "public".equals(asString(address.get("type")))) {
                return asString(address.get("ip_address"));
            }
        }

        return null;
    }
}
