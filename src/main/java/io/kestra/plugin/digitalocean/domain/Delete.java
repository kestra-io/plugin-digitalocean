package io.kestra.plugin.digitalocean.domain;

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
    title = "Delete a DigitalOcean domain zone",
    description = "Permanently removes a domain zone and all of its DNS records. This cannot be undone."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a domain zone",
            full = true,
            code = """
                id: digitalocean_delete_domain
                namespace: company.team

                tasks:
                  - id: delete_domain
                    type: io.kestra.plugin.digitalocean.domain.Delete
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    name: "example.com"
                """
        )
    }
)
public class Delete extends AbstractDigitalOceanTask implements RunnableTask<VoidOutput> {

    @Schema(title = "Domain name", description = "Fully qualified zone name to delete, e.g. example.com.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rName = requireRendered(runContext, name, String.class, "name");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Deleting DigitalOcean domain zone {}", rName);

        var url = join(rBaseUrl, "v2/domains/" + encodePathSegment(rName));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("DELETE");
        request(runContext, options, rApiToken, requestBuilder, String.class);

        return null;
    }
}
