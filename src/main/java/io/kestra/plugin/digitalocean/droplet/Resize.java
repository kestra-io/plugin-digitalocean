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
import lombok.Builder;
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
    title = "Resize a DigitalOcean droplet",
    description = """
        Triggers an asynchronous resize action on a droplet. The DigitalOcean API processes the resize \
        asynchronously; this task only reports the action's initial status, it does not wait for completion.

        Resizing the disk (`disk: true`) is permanent and can only be done once per droplet; resizing \
        without the disk only requires the droplet to be powered off first for most plans. Use \
        io.kestra.plugin.digitalocean.droplet.Action to power a droplet off first.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Power off a droplet, then resize it to a bigger plan",
            full = true,
            code = """
                id: digitalocean_resize_droplet
                namespace: company.team

                tasks:
                  - id: power_off
                    type: io.kestra.plugin.digitalocean.droplet.Action
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    dropletId: "3164444"
                    action: POWER_OFF
                  - id: resize
                    type: io.kestra.plugin.digitalocean.droplet.Resize
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    dropletId: "3164444"
                    size: "s-2vcpu-2gb"
                """
        )
    }
)
public class Resize extends AbstractDigitalOceanTask implements RunnableTask<AbstractDigitalOceanTask.ActionOutput> {

    @Schema(title = "Droplet ID", description = "Numeric identifier of the droplet to resize.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> dropletId;

    @Schema(title = "New size slug", description = "New droplet size slug, e.g. s-2vcpu-2gb.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> size;

    @Schema(title = "Resize disk", description = "Whether to also resize the disk. This is permanent and can only be done once per droplet. Defaults to false.")
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> disk = Property.ofValue(false);

    @Override
    public ActionOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rDropletId = requireRendered(runContext, dropletId, String.class, "dropletId");
        var rSize = requireRendered(runContext, size, String.class, "size");
        var rDisk = runContext.render(disk).as(Boolean.class).orElse(false);
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("type", "resize");
        payload.put("size", rSize);
        payload.put("disk", rDisk);

        logger.info("Resizing DigitalOcean droplet {} to {}", rDropletId, rSize);

        var url = join(rBaseUrl, "v2/droplets/" + encodePathSegment(rDropletId) + "/actions");
        var requestBuilder = HttpRequest.builder()
            .uri(URI.create(url))
            .method("POST")
            .body(HttpRequest.JsonRequestBody.of(payload));

        var body = requestJson(runContext, options, rApiToken, requestBuilder);
        return toActionOutput(body);
    }
}
