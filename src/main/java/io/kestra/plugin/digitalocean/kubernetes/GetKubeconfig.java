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
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Download a DigitalOcean Kubernetes cluster's kubeconfig",
    description = "Fetches the kubeconfig for a Kubernetes (DOKS) cluster and stores it in Kestra internal " +
        "storage. The kubeconfig contains a client certificate and key, so it is never returned inline as a " +
        "string output; only a storage URI is returned. Use it with a downstream task that accepts a " +
        "kubeconfig file, such as io.kestra.plugin.kubernetes tasks."
)
@Plugin(
    examples = {
        @Example(
            title = "Download a cluster's kubeconfig for use in a later task",
            full = true,
            code = """
                id: digitalocean_get_kubeconfig
                namespace: company.team

                tasks:
                  - id: get_kubeconfig
                    type: io.kestra.plugin.digitalocean.kubernetes.GetKubeconfig
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    clusterId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                """
        )
    }
)
public class GetKubeconfig extends AbstractDigitalOceanTask implements RunnableTask<GetKubeconfig.Output> {

    @Schema(title = "Cluster ID", description = "UUID of the Kubernetes cluster whose kubeconfig to download.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> clusterId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rClusterId = runContext.render(clusterId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("clusterId is required")
        );
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching kubeconfig for DigitalOcean Kubernetes cluster {}", rClusterId);

        var url = join(rBaseUrl, "v2/kubernetes/clusters/" + encodePathSegment(rClusterId) + "/kubeconfig");
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var content = request(runContext, options, rApiToken, requestBuilder, String.class).getBody();

        var tempFile = runContext.workingDir().createTempFile(".yaml").toFile();
        Files.writeString(tempFile.toPath(), content != null ? content : "", StandardCharsets.UTF_8);
        var uri = runContext.storage().putFile(tempFile);

        return Output.builder().uri(uri).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Kubeconfig URI", description = "URI of the kubeconfig YAML file in Kestra internal storage.")
        private final URI uri;
    }
}
