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
    title = "Resize a DigitalOcean volume",
    description = """
        Triggers an asynchronous action growing a block storage volume. Volumes can only be resized \
        upwards, up to 16 TiB (16384 GiB). This task only reports the action's initial status; use \
        io.kestra.plugin.digitalocean.volume.GetAction with the returned actionId to check when it completes.

        Resizing the volume does not grow the filesystem on it: once the action completes, expand the \
        filesystem from the droplet the volume is attached to (for example with resize2fs for ext4)."""
)
@Plugin(
    examples = {
        @Example(
            title = "Grow a volume to 200 GiB",
            full = true,
            code = """
                id: digitalocean_resize_volume
                namespace: company.team

                tasks:
                  - id: resize_volume
                    type: io.kestra.plugin.digitalocean.volume.Resize
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    volumeId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                    sizeGigabytes: 200
                    region: "nyc3"
                """
        )
    }
)
public class Resize extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.ActionOutput> {

    private static final int MAX_SIZE_GIGABYTES = 16384;

    @Schema(title = "Volume ID", description = "UUID of the volume to resize.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> volumeId;

    @Schema(
        title = "New size in GiB",
        description = "New size of the volume in GiB, between 1 and 16384. Must be larger than the current size."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<Integer> sizeGigabytes;

    @Schema(title = "Region", description = "Datacenter region slug the volume lives in, e.g. nyc3.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> region;

    @Override
    public ActionOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rVolumeId = requireRendered(runContext, volumeId, String.class, "volumeId");
        var rSizeGigabytes = requireInRange(
            "sizeGigabytes",
            requireRendered(runContext, sizeGigabytes, Integer.class, "sizeGigabytes"),
            1,
            MAX_SIZE_GIGABYTES
        );
        var rRegion = requireRendered(runContext, region, String.class, "region");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = VolumeActionPayload.buildResize(rSizeGigabytes, rRegion);

        logger.info("Resizing DigitalOcean volume {} to {} GiB", rVolumeId, rSizeGigabytes);

        var url = join(rBaseUrl, "v2/volumes/" + encodePathSegment(rVolumeId) + "/actions");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return toActionOutput(body);
    }
}
