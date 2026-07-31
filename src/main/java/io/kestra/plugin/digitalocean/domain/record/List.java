package io.kestra.plugin.digitalocean.domain.record;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanListTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "List DNS records of a DigitalOcean domain",
    description = "Lists DNS records for a domain, following DigitalOcean's page-based pagination automatically."
)
@Plugin(
    examples = {
        @Example(
            title = "List all records of a domain",
            full = true,
            code = """
                id: digitalocean_list_records
                namespace: company.team

                tasks:
                  - id: list_records
                    type: io.kestra.plugin.digitalocean.domain.record.List
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    domain: "example.com"
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_records.total }} record(s)"
                """
        )
    }
)
public class List extends AbstractDigitalOceanListTask {

    @Schema(title = "Domain", description = "Domain name whose records to list, e.g. example.com.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> domain;

    @Override
    protected String path(RunContext runContext) throws Exception {
        var rDomain = requireRendered(runContext, domain, String.class, "domain");
        return "v2/domains/" + encodePathSegment(rDomain) + "/records";
    }

    @Override
    protected String arrayKey() {
        return "domain_records";
    }

    @Override
    protected String resourceLabel() {
        return "record(s)";
    }
}
