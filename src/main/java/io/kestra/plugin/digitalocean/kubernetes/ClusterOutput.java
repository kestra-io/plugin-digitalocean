package io.kestra.plugin.digitalocean.kubernetes;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asMap;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

/** Shared output shape for {@link Get} and {@link Create}: both return the same cluster JSON object. */
@Builder
@Getter
public class ClusterOutput implements Output {

    @Schema(title = "Cluster ID", description = "UUID assigned by DigitalOcean.")
    private final String id;

    @Schema(title = "Cluster name")
    private final String name;

    @Schema(title = "Region slug", description = "Datacenter region the cluster runs in, e.g. nyc1.")
    private final String region;

    @Schema(title = "Kubernetes version")
    private final String version;

    @Schema(title = "Cluster status", description = "One of running, provisioning, degraded, error, deleting, or upgrading.")
    private final String status;

    @Schema(title = "API server endpoint")
    private final String endpoint;

    @Schema(title = "Creation timestamp")
    private final Instant createdAt;

    public static ClusterOutput from(Map<String, Object> cluster) {
        var status = asMap(cluster.get("status"));
        var createdAt = asString(cluster.get("created_at"));

        return ClusterOutput.builder()
            .id(asString(cluster.get("id")))
            .name(asString(cluster.get("name")))
            .region(asString(cluster.get("region")))
            .version(asString(cluster.get("version")))
            .status(status != null ? asString(status.get("state")) : null)
            .endpoint(asString(cluster.get("endpoint")))
            .createdAt(createdAt != null ? Instant.parse(createdAt) : null)
            .build();
    }
}
