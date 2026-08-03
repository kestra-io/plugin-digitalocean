package io.kestra.plugin.digitalocean.loadbalancer;

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
    title = "List DigitalOcean load balancers",
    description = "Lists load balancers on the account, following DigitalOcean's page-based pagination automatically."
)
@Plugin(
    examples = {
        @Example(
            title = "List all load balancers and log how many exist",
            full = true,
            code = """
                id: digitalocean_list_load_balancers
                namespace: company.team

                tasks:
                  - id: list_load_balancers
                    type: io.kestra.plugin.digitalocean.loadbalancer.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_load_balancers.total }} load balancer(s)"
                """
        )
    }
)
public class List extends AbstractDigitalOceanListTask {

    @Override
    protected String path(RunContext runContext) {
        return "v2/load_balancers";
    }

    @Override
    protected String arrayKey() {
        return "load_balancers";
    }

    @Override
    protected String resourceLabel() {
        return "load balancer(s)";
    }
}
