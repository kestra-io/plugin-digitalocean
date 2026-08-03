package io.kestra.plugin.digitalocean.loadbalancer;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asMap;
import static io.kestra.plugin.digitalocean.AbstractDigitalOceanTask.asString;

/** Shared output shape for {@link Get}, {@link Create}, and {@link Update}: all return the same load balancer JSON object. */
@Builder
@Getter
public class LoadBalancerOutput implements Output {

    @Schema(title = "Load balancer ID", description = "UUID assigned by DigitalOcean.")
    private final String id;

    @Schema(title = "Load balancer name")
    private final String name;

    @Schema(title = "Public IP address")
    private final String ip;

    @Schema(title = "Status", description = "One of new, active, or errored.")
    private final String status;

    @Schema(title = "Region slug", description = "Datacenter region the load balancer runs in, e.g. nyc3.")
    private final String region;

    public static LoadBalancerOutput from(Map<String, Object> loadBalancer) {
        var region = asMap(loadBalancer.get("region"));

        return LoadBalancerOutput.builder()
            .id(asString(loadBalancer.get("id")))
            .name(asString(loadBalancer.get("name")))
            .ip(asString(loadBalancer.get("ip")))
            .status(asString(loadBalancer.get("status")))
            .region(region != null ? asString(region.get("slug")) : null)
            .build();
    }
}
