package io.kestra.plugin.digitalocean.firewall;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

/** Shared output shape for {@link Get} and {@link Create}: both return the same firewall JSON object. */
@Builder
@Getter
public class FirewallOutput implements Output {

    @Schema(title = "Firewall ID", description = "UUID assigned by DigitalOcean.")
    private final String id;

    @Schema(title = "Firewall name")
    private final String name;

    @Schema(title = "Firewall status", description = "One of waiting, succeeded, or failed.")
    private final String status;

    @Schema(title = "Creation timestamp")
    private final Instant createdAt;

    public static FirewallOutput from(Map<String, Object> firewall) {
        var createdAt = asString(firewall.get("created_at"));

        return FirewallOutput.builder()
            .id(asString(firewall.get("id")))
            .name(asString(firewall.get("name")))
            .status(asString(firewall.get("status")))
            .createdAt(createdAt != null ? Instant.parse(createdAt) : null)
            .build();
    }
}
