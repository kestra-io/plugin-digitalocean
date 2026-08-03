package io.kestra.plugin.digitalocean.droplet;

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
    title = "List DigitalOcean droplets",
    description = "Lists droplets on the account, following DigitalOcean's page-based pagination automatically."
)
@Plugin(
    examples = {
        @Example(
            title = "List all droplets and log how many exist",
            full = true,
            code = """
                id: digitalocean_list_droplets
                namespace: company.team

                tasks:
                  - id: list_droplets
                    type: io.kestra.plugin.digitalocean.droplet.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_droplets.total }} droplet(s)"
                """
        )
    }
)
public class List extends AbstractDigitalOceanListTask {

    @Override
    protected String path(RunContext runContext) {
        return "v2/droplets";
    }

    @Override
    protected String arrayKey() {
        return "droplets";
    }

    @Override
    protected String resourceLabel() {
        return "droplet(s)";
    }
}
