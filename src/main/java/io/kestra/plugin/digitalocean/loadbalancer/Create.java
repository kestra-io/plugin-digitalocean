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
    title = "Create a DigitalOcean load balancer",
    description = "Creates a new load balancer with at least one forwarding rule, optionally attached to a set of droplets."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a load balancer forwarding HTTP traffic to a set of droplets",
            full = true,
            code = """
                id: digitalocean_create_load_balancer
                namespace: company.team

                tasks:
                  - id: create_load_balancer
                    type: io.kestra.plugin.digitalocean.loadbalancer.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    name: "web-lb"
                    region: "nyc3"
                    forwardingRules:
                      - entry_protocol: "http"
                        entry_port: 80
                        target_protocol: "http"
                        target_port: 80
                    dropletIds:
                      - 3164444
                      - 3164445
                """
        )
    }
)
public class Create extends AbstractDigitalOceanTask implements RunnableTask<LoadBalancerOutput> {

    @Schema(title = "Load balancer name", description = "Human-readable name for the load balancer, must be unique on the account.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Region", description = "Datacenter region slug to create the load balancer in, e.g. nyc3, ams3, sgp1.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> region;

    @Schema(
        title = "Forwarding rules",
        description = "At least one forwarding rule, each with `entry_protocol`, `entry_port`, `target_protocol`, and `target_port`."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> forwardingRules;

    @Schema(title = "Droplet IDs", description = "Numeric IDs of the droplets to attach to the load balancer.")
    @PluginProperty(group = "advanced")
    private Property<List<Long>> dropletIds;

    @Override
    public LoadBalancerOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
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

        logger.info("Creating DigitalOcean load balancer '{}' in {}", rName, rRegion);

        var url = join(rBaseUrl, "v2/load_balancers");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return LoadBalancerOutput.from(unwrap(body, "load_balancer"));
    }
}
