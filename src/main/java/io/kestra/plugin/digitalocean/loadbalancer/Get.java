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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get a DigitalOcean load balancer",
    description = "Reads a single load balancer's details from the DigitalOcean API."
)
@Plugin(
    examples = {
        @Example(
            title = "Get a load balancer and log its public IP",
            full = true,
            code = """
                id: digitalocean_get_load_balancer
                namespace: company.team

                tasks:
                  - id: get_load_balancer
                    type: io.kestra.plugin.digitalocean.loadbalancer.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    loadBalancerId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                  - id: log_ip
                    type: io.kestra.plugin.core.log.Log
                    message: "Load balancer IP: {{ outputs.get_load_balancer.ip }}"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<LoadBalancerOutput> {

    @Schema(title = "Load balancer ID", description = "UUID of the load balancer to read.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> loadBalancerId;

    @Override
    public LoadBalancerOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rLoadBalancerId = requireRendered(runContext, loadBalancerId, String.class, "loadBalancerId");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching DigitalOcean load balancer {}", rLoadBalancerId);

        var url = join(rBaseUrl, "v2/load_balancers/" + encodePathSegment(rLoadBalancerId));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return LoadBalancerOutput.from(unwrap(body, "load_balancer"));
    }
}
