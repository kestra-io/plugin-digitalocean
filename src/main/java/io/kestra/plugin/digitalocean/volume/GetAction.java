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
    title = "Get a DigitalOcean volume action",
    description = "Reads the current status of an action run on a block storage volume, such as the one returned by " +
        "io.kestra.plugin.digitalocean.volume.Resize, Attach, or Detach."
)
@Plugin(
    examples = {
        @Example(
            title = "Resize a volume, then check the resize action's status",
            full = true,
            code = """
                id: digitalocean_volume_action_status
                namespace: company.team

                tasks:
                  - id: resize_volume
                    type: io.kestra.plugin.digitalocean.volume.Resize
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    volumeId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                    sizeGigabytes: 200
                    region: "nyc3"
                  - id: get_action
                    type: io.kestra.plugin.digitalocean.volume.GetAction
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    volumeId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                    actionId: "{{ outputs.resize_volume.actionId }}"
                  - id: log_status
                    type: io.kestra.plugin.core.log.Log
                    message: "Resize is {{ outputs.get_action.status }}"
                """
        )
    }
)
public class GetAction extends AbstractDigitalOceanTask implements RunnableTask<VolumeActionOutput> {

    @Schema(title = "Volume ID", description = "UUID of the volume the action ran on.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> volumeId;

    @Schema(title = "Action ID", description = "Numeric identifier of the action, e.g. the actionId output of Resize, Attach, or Detach.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> actionId;

    @Override
    public VolumeActionOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rVolumeId = requireRendered(runContext, volumeId, String.class, "volumeId");
        var rActionId = requireRendered(runContext, actionId, String.class, "actionId");
        var rApiToken = renderApiToken(runContext);
        var rBaseUrl = renderBaseUrl(runContext);

        logger.info("Fetching action {} of DigitalOcean volume {}", rActionId, rVolumeId);

        var url = join(rBaseUrl, "v2/volumes/" + encodePathSegment(rVolumeId) + "/actions/" + encodePathSegment(rActionId));
        var requestBuilder = HttpRequest.builder().uri(URI.create(url)).method("GET");
        var body = requestJson(runContext, options, rApiToken, requestBuilder);

        return VolumeActionOutput.from(unwrap(body, "action"));
    }
}
