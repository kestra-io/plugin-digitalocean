package io.kestra.plugin.digitalocean.volume;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.digitalocean.AbstractDigitalOceanListTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List the actions of a DigitalOcean volume",
    description = "Lists every action run on a block storage volume (attach, detach, resize), following " +
        "DigitalOcean's page-based pagination automatically."
)
@Plugin(
    examples = {
        @Example(
            title = "List a volume's actions and log how many ran",
            full = true,
            code = """
                id: digitalocean_list_volume_actions
                namespace: company.team

                tasks:
                  - id: list_actions
                    type: io.kestra.plugin.digitalocean.volume.ListActions
                    apiToken: "{{ secret('DIGITALOCEAN_TOKEN') }}"
                    volumeId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
                  - id: log_total
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_actions.total }} action(s) on the volume"
                """
        )
    }
)
public class ListActions extends AbstractDigitalOceanListTask {

    @Schema(title = "Volume ID", description = "UUID of the volume whose actions to list.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> volumeId;

    @Override
    protected String path(RunContext runContext) throws Exception {
        var rVolumeId = requireRendered(runContext, volumeId, String.class, "volumeId");
        return "v2/volumes/" + encodePathSegment(rVolumeId) + "/actions";
    }

    @Override
    protected String arrayKey() {
        return "actions";
    }

    @Override
    protected String resourceLabel() {
        return "volume action(s)";
    }
}
