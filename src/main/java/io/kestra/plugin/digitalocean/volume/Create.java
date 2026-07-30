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
import java.util.LinkedHashMap;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a DigitalOcean volume",
    description = "Creates a new block storage volume that can then be attached to a droplet."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a 100 GiB volume in nyc3",
            full = true,
            code = """
                id: digitalocean_create_volume
                namespace: company.team

                tasks:
                  - id: create_volume
                    type: io.kestra.plugin.digitalocean.volume.Create
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    name: "data-volume"
                    region: "nyc3"
                    sizeGigabytes: 100
                    filesystemType: "ext4"
                """
        )
    }
)
public class Create extends AbstractDigitalOceanTask implements RunnableTask<VolumeOutput> {

    @Schema(title = "Volume name", description = "Human-readable name for the volume, must be unique per region.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(title = "Region", description = "Datacenter region slug to create the volume in, e.g. nyc3, ams3, sgp1.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> region;

    @Schema(title = "Size in GiB", description = "Volume size in gibibytes, from 1 up to 16384 (16 TiB).")
    @NotNull
    @PluginProperty(group = "main")
    private Property<Long> sizeGigabytes;

    @Schema(title = "Description", description = "Optional free-text description for the volume.")
    @PluginProperty(group = "advanced")
    private Property<String> volumeDescription;

    @Schema(title = "Filesystem type", description = "Optional filesystem to format the volume with at creation time: ext4 or xfs. Leave empty to create an unformatted volume.")
    @PluginProperty(group = "advanced")
    private Property<String> filesystemType;

    @Override
    public VolumeOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rName = runContext.render(name).as(String.class).orElseThrow(() -> new IllegalArgumentException("name is required"));
        var rRegion = runContext.render(region).as(String.class).orElseThrow(() -> new IllegalArgumentException("region is required"));
        var rSizeGigabytes = requireInRange("sizeGigabytes", runContext.render(sizeGigabytes).as(Long.class).orElseThrow(() -> new IllegalArgumentException("sizeGigabytes is required")), 1, 16384);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", rName);
        payload.put("region", rRegion);
        payload.put("size_gigabytes", rSizeGigabytes);
        runContext.render(volumeDescription).as(String.class).ifPresent(v -> payload.put("description", v));
        runContext.render(filesystemType).as(String.class).ifPresent(v -> payload.put("filesystem_type", v));

        logger.info("Creating DigitalOcean volume '{}' ({} GiB) in {}", rName, rSizeGigabytes, rRegion);

        var url = join(rBaseUrl, "v2/volumes");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return VolumeOutput.from(unwrap(body, "volume"));
    }
}
