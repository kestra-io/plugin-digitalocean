package io.kestra.plugin.digitalocean.droplet;

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
    title = "Delete a DigitalOcean droplet",
    description = "Permanently destroys a droplet and its associated disk. This cannot be undone; snapshots and backups are not affected."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a droplet",
            full = true,
            code = """
                id: digitalocean_delete_droplet
                namespace: company.team

                tasks:
                  - id: delete_droplet
                    type: io.kestra.plugin.digitalocean.droplet.Delete
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    dropletId: "3164444"
                """
        )
    }
)
public class Delete extends AbstractDigitalOceanTask implements RunnableTask<VoidOutput> {

    @Schema(title = "Droplet ID", description = "Numeric identifier of the droplet to delete.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> dropletId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDropletId = requireRendered(runContext, dropletId, String.class, "dropletId");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Deleting DigitalOcean droplet {}", rDropletId);

        var url = join(rBaseUrl, "v2/droplets/" + encodePathSegment(rDropletId));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("DELETE");
        request(runContext, options, rApiToken, requestBuilder, String.class);

        return null;
    }
}
