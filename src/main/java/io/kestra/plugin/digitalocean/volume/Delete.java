package io.kestra.plugin.digitalocean.volume;

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
    title = "Delete a DigitalOcean volume",
    description = "Permanently destroys a block storage volume and its data. The volume must first be detached from any droplet."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a volume",
            full = true,
            code = """
                id: digitalocean_delete_volume
                namespace: company.team

                tasks:
                  - id: delete_volume
                    type: io.kestra.plugin.digitalocean.volume.Delete
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    volumeId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                """
        )
    }
)
public class Delete extends AbstractDigitalOceanTask implements RunnableTask<VoidOutput> {

    @Schema(title = "Volume ID", description = "UUID of the volume to delete.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> volumeId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rVolumeId = runContext.render(volumeId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("volumeId is required")
        );
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Deleting DigitalOcean volume {}", rVolumeId);

        var url = join(rBaseUrl, "v2/volumes/" + rVolumeId);
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("DELETE");
        request(runContext, options, rApiToken, requestBuilder, String.class);

        return new VoidOutput();
    }
}
