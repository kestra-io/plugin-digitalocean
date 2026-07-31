package io.kestra.plugin.digitalocean.droplet;

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
    title = "Get a DigitalOcean droplet",
    description = "Reads a single droplet's details from the DigitalOcean API."
)
@Plugin(
    examples = {
        @Example(
            title = "Get a droplet and log its status",
            full = true,
            code = """
                id: digitalocean_get_droplet
                namespace: company.team

                tasks:
                  - id: get_droplet
                    type: io.kestra.plugin.digitalocean.droplet.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    dropletId: "3164444"
                  - id: log_status
                    type: io.kestra.plugin.core.log.Log
                    message: "Droplet {{ outputs.get_droplet.name }} is {{ outputs.get_droplet.status }}"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<DropletOutput> {

    @Schema(title = "Droplet ID", description = "Numeric identifier of the droplet to read.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> dropletId;

    @Override
    public DropletOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDropletId = requireRendered(runContext, dropletId, String.class, "dropletId");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching DigitalOcean droplet {}", rDropletId);

        var url = join(rBaseUrl, "v2/droplets/" + encodePathSegment(rDropletId));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return DropletOutput.from(unwrap(body, "droplet"));
    }
}
