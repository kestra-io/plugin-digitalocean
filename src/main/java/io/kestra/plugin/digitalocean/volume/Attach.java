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
    title = "Attach a DigitalOcean volume to a droplet",
    description = "Triggers an asynchronous action attaching a block storage volume to a droplet. Both must be in the same region."
)
@Plugin(
    examples = {
        @Example(
            title = "Attach a volume to a droplet",
            full = true,
            code = """
                id: digitalocean_attach_volume
                namespace: company.team

                tasks:
                  - id: attach_volume
                    type: io.kestra.plugin.digitalocean.volume.Attach
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    volumeId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                    dropletId: "3164444"
                    region: "nyc3"
                """
        )
    }
)
public class Attach extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.ActionOutput> {

    @Schema(title = "Volume ID", description = "UUID of the volume to attach.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> volumeId;

    @Schema(title = "Droplet ID", description = "Numeric identifier of the droplet to attach the volume to.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> dropletId;

    @Schema(title = "Region", description = "Datacenter region slug both the volume and droplet live in, e.g. nyc3.")
    @PluginProperty(group = "main")
    private Property<String> region;

    @Override
    public ActionOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rVolumeId = runContext.render(volumeId).as(String.class).orElseThrow(() -> new IllegalArgumentException("volumeId is required"));
        var rDropletId = runContext.render(dropletId).as(String.class).orElseThrow(() -> new IllegalArgumentException("dropletId is required"));
        var rRegion = runContext.render(region).as(String.class).orElse(null);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = VolumeActionPayload.build("attach", VolumeActionPayload.parseDropletId(rDropletId), rRegion);

        logger.info("Attaching DigitalOcean volume {} to droplet {}", rVolumeId, rDropletId);

        var url = join(rBaseUrl, "v2/volumes/" + encodePathSegment(rVolumeId) + "/actions");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return toActionOutput(body);
    }
}
