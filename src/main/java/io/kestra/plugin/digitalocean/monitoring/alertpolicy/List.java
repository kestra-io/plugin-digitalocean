package io.kestra.plugin.digitalocean.monitoring.alertpolicy;

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
    title = "List DigitalOcean monitoring alert policies",
    description = "Lists all alert policies on the account, following DigitalOcean's page-based pagination automatically."
)
@Plugin(
    examples = {
        @Example(
            title = "List alert policies and log the total count",
            full = true,
            code = """
                id: digitalocean_list_alert_policies
                namespace: company.team

                tasks:
                  - id: list_policies
                    type: io.kestra.plugin.digitalocean.monitoring.alertpolicy.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                  - id: log_count
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_policies.total }} alert policy(ies)"
                """
        )
    }
)
public class List extends AbstractDigitalOceanListTask {

    @Override
    protected String path(RunContext runContext) {
        return "v2/monitoring/alerts";
    }

    @Override
    protected String arrayKey() {
        return "policies";
    }

    @Override
    protected String resourceLabel() {
        return "alert policy(ies)";
    }
}
