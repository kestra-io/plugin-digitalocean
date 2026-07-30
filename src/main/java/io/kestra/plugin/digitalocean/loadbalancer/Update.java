package io.kestra.plugin.digitalocean.loadbalancer;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Update a DigitalOcean load balancer",
    description = "Replaces a load balancer's full configuration. DigitalOcean's update endpoint expects the " +
        "complete desired state, not a partial patch: name, region, forwarding rules, and attached droplets " +
        "are all replaced by the values given here."
)
@Plugin(
    examples = {
        @Example(
            title = "Add HTTPS forwarding to an existing load balancer",
            full = true,
            code = """
                id: digitalocean_update_load_balancer
                namespace: company.team

                tasks:
                  - id: update_load_balancer
                    type: io.kestra.plugin.digitalocean.loadbalancer.Update
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    loadBalancerId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                    name: "web-lb"
                    region: "nyc3"
                    forwardingRules:
                      - entry_protocol: "http"
                        entry_port: 80
                        target_protocol: "http"
                        target_port: 80
                      - entry_protocol: "https"
                        entry_port: 443
                        target_protocol: "http"
                        target_port: 80
                        tls_passthrough: false
                    dropletIds:
                      - 3164444
                      - 3164445
                """
        )
    }
)
public class Update extends AbstractDigitalOceanTask implements RunnableTask<LoadBalancerOutput> {

    @Schema(title = "Load balancer ID", description = "UUID of the load balancer to update.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> loadBalancerId;

    @Schema(title = "Load balancer name")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Region", description = "Datacenter region slug the load balancer runs in, e.g. nyc3.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> region;

    @Schema(
        title = "Forwarding rules",
        description = "Full replacement list of forwarding rules, each with `entry_protocol`, `entry_port`, `target_protocol`, and `target_port`."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> forwardingRules;

    @Schema(title = "Droplet IDs", description = "Full replacement list of numeric droplet IDs attached to the load balancer.")
    @PluginProperty(group = "advanced")
    private Property<List<Long>> dropletIds;

    @Override
    public LoadBalancerOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rLoadBalancerId = runContext.render(loadBalancerId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("loadBalancerId is required")
        );
        var rName = runContext.render(name).as(String.class).orElseThrow(() -> new IllegalArgumentException("name is required"));
        var rRegion = runContext.render(region).as(String.class).orElseThrow(() -> new IllegalArgumentException("region is required"));
        var rForwardingRules = runContext.render(forwardingRules).asList(Map.class);
        if (rForwardingRules.isEmpty()) {
            throw new IllegalArgumentException("forwardingRules must contain at least one forwarding rule");
        }
        var rDropletIds = runContext.render(dropletIds).asList(Long.class);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = LoadBalancerPayload.build(rName, rRegion, rForwardingRules, rDropletIds);

        logger.info("Updating DigitalOcean load balancer {}", rLoadBalancerId);

        var url = join(rBaseUrl, "v2/load_balancers/" + rLoadBalancerId);
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("PUT")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return LoadBalancerOutput.from(unwrap(body, "load_balancer"));
    }
}
