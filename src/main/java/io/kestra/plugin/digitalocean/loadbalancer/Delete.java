package io.kestra.plugin.digitalocean.loadbalancer;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
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
    title = "Delete a DigitalOcean load balancer",
    description = "Permanently destroys a load balancer. This cannot be undone; attached droplets are not affected."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a load balancer",
            full = true,
            code = """
                id: digitalocean_delete_load_balancer
                namespace: company.team

                tasks:
                  - id: delete_load_balancer
                    type: io.kestra.plugin.digitalocean.loadbalancer.Delete
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    loadBalancerId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                """
        )
    }
)
public class Delete extends AbstractDigitalOceanTask implements RunnableTask<VoidOutput> {

    @Schema(title = "Load balancer ID", description = "UUID of the load balancer to delete.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> loadBalancerId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rLoadBalancerId = runContext.render(loadBalancerId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("loadBalancerId is required")
        );
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Deleting DigitalOcean load balancer {}", rLoadBalancerId);

        var url = join(rBaseUrl, "v2/load_balancers/" + rLoadBalancerId);
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("DELETE");
        request(runContext, options, rApiToken, requestBuilder, String.class);

        return new VoidOutput();
    }
}
