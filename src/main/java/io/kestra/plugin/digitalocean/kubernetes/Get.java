package io.kestra.plugin.digitalocean.kubernetes;

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
    title = "Get a DigitalOcean Kubernetes cluster",
    description = "Reads a single Kubernetes (DOKS) cluster's details from the DigitalOcean API."
)
@Plugin(
    examples = {
        @Example(
            title = "Get a cluster and log its status",
            full = true,
            code = """
                id: digitalocean_get_cluster
                namespace: company.team

                tasks:
                  - id: get_cluster
                    type: io.kestra.plugin.digitalocean.kubernetes.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    clusterId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                  - id: log_status
                    type: io.kestra.plugin.core.log.Log
                    message: "Cluster {{ outputs.get_cluster.name }} is {{ outputs.get_cluster.status }}"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<ClusterOutput> {

    @Schema(title = "Cluster ID", description = "UUID of the Kubernetes cluster to read.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> clusterId;

    @Override
    public ClusterOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rClusterId = runContext.render(clusterId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("clusterId is required")
        );
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching DigitalOcean Kubernetes cluster {}", rClusterId);

        var url = join(rBaseUrl, "v2/kubernetes/clusters/" + rClusterId);
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return ClusterOutput.from(unwrap(body, "kubernetes_cluster"));
    }
}
