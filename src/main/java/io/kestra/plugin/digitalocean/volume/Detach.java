package io.kestra.plugin.digitalocean.volume;

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
    title = "Detach a DigitalOcean volume from a droplet",
    description = "Triggers an asynchronous action detaching a block storage volume from the droplet it is currently attached to."
)
@Plugin(
    examples = {
        @Example(
            title = "Detach a volume from a droplet",
            full = true,
            code = """
                id: digitalocean_detach_volume
                namespace: company.team

                tasks:
                  - id: detach_volume
                    type: io.kestra.plugin.digitalocean.volume.Detach
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    volumeId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                    dropletId: "3164444"
                    region: "nyc3"
                """
        )
    }
)
public class Detach extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.ActionOutput> {

    @Schema(title = "Volume ID", description = "UUID of the volume to detach.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> volumeId;

    @Schema(title = "Droplet ID", description = "Numeric identifier of the droplet to detach the volume from.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> dropletId;

    @Schema(title = "Region", description = "Datacenter region slug both the volume and droplet live in, e.g. nyc3.")
    @PluginProperty(group = "main")
    private Property<String> region;

    @Override
    public ActionOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rVolumeId = requireRendered(runContext, volumeId, String.class, "volumeId");
        var rDropletId = requireRendered(runContext, dropletId, String.class, "dropletId");
        var rRegion = runContext.render(region).as(String.class).orElse(null);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = VolumeActionPayload.build("detach", VolumeActionPayload.parseDropletId(rDropletId), rRegion);

        logger.info("Detaching DigitalOcean volume {} from droplet {}", rVolumeId, rDropletId);

        var url = join(rBaseUrl, "v2/volumes/" + encodePathSegment(rVolumeId) + "/actions");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return toActionOutput(body);
    }
}
