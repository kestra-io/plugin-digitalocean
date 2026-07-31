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
    title = "Get a DigitalOcean volume",
    description = "Reads a single block storage volume's details from the DigitalOcean API."
)
@Plugin(
    examples = {
        @Example(
            title = "Get a volume and log its size",
            full = true,
            code = """
                id: digitalocean_get_volume
                namespace: company.team

                tasks:
                  - id: get_volume
                    type: io.kestra.plugin.digitalocean.volume.Get
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    volumeId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                  - id: log_size
                    type: io.kestra.plugin.core.log.Log
                    message: "Volume {{ outputs.get_volume.name }} is {{ outputs.get_volume.sizeGigabytes }} GiB"
                """
        )
    }
)
public class Get extends AbstractDigitalOceanTask implements RunnableTask<VolumeOutput> {

    @Schema(title = "Volume ID", description = "UUID of the volume to read.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> volumeId;

    @Override
    public VolumeOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rVolumeId = requireRendered(runContext, volumeId, String.class, "volumeId");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching DigitalOcean volume {}", rVolumeId);

        var url = join(rBaseUrl, "v2/volumes/" + encodePathSegment(rVolumeId));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return VolumeOutput.from(unwrap(body, "volume"));
    }
}
