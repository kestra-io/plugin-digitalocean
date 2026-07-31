package io.kestra.plugin.digitalocean.kubernetes;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanListTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List DigitalOcean Kubernetes clusters",
    description = "Lists Kubernetes (DOKS) clusters on the account, following DigitalOcean's page-based pagination automatically."
)
@Plugin(
    examples = {
        @Example(
            title = "List all Kubernetes clusters and log how many exist",
            full = true,
            code = """
                id: digitalocean_list_clusters
                namespace: company.team

                tasks:
                  - id: list_clusters
                    type: io.kestra.plugin.digitalocean.kubernetes.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_clusters.total }} cluster(s)"
                """
        )
    }
)
public class List extends AbstractDigitalOceanListTask {

    @Override
    protected String path(RunContext runContext) {
        return "v2/kubernetes/clusters";
    }

    @Override
    protected String arrayKey() {
        return "kubernetes_clusters";
    }

    @Override
    protected String resourceLabel() {
        return "cluster(s)";
    }
}
