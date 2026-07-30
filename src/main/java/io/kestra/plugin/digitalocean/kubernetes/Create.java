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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a DigitalOcean Kubernetes cluster",
    description = "Creates a new Kubernetes (DOKS) cluster with at least one node pool. Provisioning a cluster " +
        "typically takes several minutes; this task returns as soon as the API accepts the request, without waiting for it to become ready."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a 3-node Kubernetes cluster in nyc1",
            full = true,
            code = """
                id: digitalocean_create_cluster
                namespace: company.team

                tasks:
                  - id: create_cluster
                    type: io.kestra.plugin.digitalocean.kubernetes.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    name: "prod-cluster"
                    region: "nyc1"
                    kubernetesVersion: "1.30.2-do.0"
                    nodePools:
                      - name: "worker-pool"
                        size: "s-2vcpu-4gb"
                        count: 3
                """
        )
    }
)
public class Create extends AbstractDigitalOceanTask implements RunnableTask<ClusterOutput> {

    @Schema(title = "Cluster name", description = "Human-readable name for the cluster, must be unique on the account.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Region", description = "Datacenter region slug to create the cluster in, e.g. nyc1, ams3, sgp1.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> region;

    @Schema(title = "Kubernetes version", description = "Kubernetes release to provision, e.g. 1.30.2-do.0. Use the DigitalOcean API's options endpoint to list valid values.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> kubernetesVersion;

    @Schema(
        title = "Node pools",
        description = "At least one node pool, each with `name`, `size` (droplet size slug), and `count` (number of nodes)."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> nodePools;

    @Override
    public ClusterOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rName = runContext.render(name).as(String.class).orElseThrow(() -> new IllegalArgumentException("name is required"));
        var rRegion = runContext.render(region).as(String.class).orElseThrow(() -> new IllegalArgumentException("region is required"));
        var rVersion = runContext.render(kubernetesVersion).as(String.class).orElseThrow(() -> new IllegalArgumentException("kubernetesVersion is required"));
        var rNodePools = runContext.render(nodePools).asList(Map.class);
        if (rNodePools.isEmpty()) {
            throw new IllegalArgumentException("nodePools must contain at least one node pool");
        }
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", rName);
        payload.put("region", rRegion);
        payload.put("version", rVersion);
        payload.put("node_pools", rNodePools);

        logger.info("Creating DigitalOcean Kubernetes cluster '{}' in {}", rName, rRegion);

        var url = join(rBaseUrl, "v2/kubernetes/clusters");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return ClusterOutput.from(unwrap(body, "kubernetes_cluster"));
    }
}
